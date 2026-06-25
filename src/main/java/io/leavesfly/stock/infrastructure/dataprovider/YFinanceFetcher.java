package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Yahoo Finance数据源适配器
 *
 * 用于获取美股、港股、日股等国际市场数据
 */
@Component
public class YFinanceFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(YFinanceFetcher.class);
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YFinanceFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "yfinance";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String symbol = convertToYahooSymbol(stockCode);
            long period1 = startDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            long period2 = endDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);

            String url = YAHOO_CHART_URL + symbol + "?" +
                    "period1=" + period1 +
                    "&period2=" + period2 +
                    "&interval=1d" +
                    "&events=history";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyList();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode result = root.path("chart").path("result");

                if (!result.isArray() || result.isEmpty()) {
                    return Collections.emptyList();
                }

                JsonNode chartData = result.get(0);
                JsonNode timestamps = chartData.path("timestamp");
                JsonNode indicators = chartData.path("indicators").path("quote").get(0);

                if (timestamps.isMissingNode() || !timestamps.isArray()) {
                    return Collections.emptyList();
                }

                List<StockDailyData> dataList = new ArrayList<>();
                JsonNode opens = indicators.path("open");
                JsonNode highs = indicators.path("high");
                JsonNode lows = indicators.path("low");
                JsonNode closes = indicators.path("close");
                JsonNode volumes = indicators.path("volume");

                for (int i = 0; i < timestamps.size(); i++) {
                    StockDailyData data = new StockDailyData();
                    data.setStockCode(stockCode);
                    data.setTradeDate(LocalDate.ofEpochDay(timestamps.get(i).asLong() / 86400));
                    data.setOpenPrice(getDoubleOrNull(opens, i));
                    data.setHighPrice(getDoubleOrNull(highs, i));
                    data.setLowPrice(getDoubleOrNull(lows, i));
                    data.setClosePrice(getDoubleOrNull(closes, i));
                    data.setVolume(volumes.has(i) ? volumes.get(i).asLong(0) : 0L);
                    data.setDataSource("yfinance");

                    // 计算涨跌幅
                    if (i > 0 && data.getClosePrice() != null) {
                        Double prevClose = getDoubleOrNull(closes, i - 1);
                        if (prevClose != null && prevClose > 0) {
                            data.setChangePct((data.getClosePrice() - prevClose) / prevClose * 100);
                        }
                    }
                    dataList.add(data);
                }
                return dataList;
            }
        } catch (Exception e) {
            log.error("YFinance获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String symbol = convertToYahooSymbol(stockCode);
            // 使用chart接口获取最新数据，range=1d
            String url = YAHOO_CHART_URL + symbol + "?range=1d&interval=1m";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode meta = root.path("chart").path("result").get(0).path("meta");

                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("stock_name", meta.path("shortName").asText(symbol));
                quote.put("current_price", meta.path("regularMarketPrice").asDouble());
                quote.put("previous_close", meta.path("previousClose").asDouble());
                double prevClose = meta.path("previousClose").asDouble();
                double curPrice = meta.path("regularMarketPrice").asDouble();
                if (prevClose > 0) {
                    quote.put("change_pct", (curPrice - prevClose) / prevClose * 100);
                }
                return quote;
            }
        } catch (Exception e) {
            log.error("YFinance获取实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        return getRealtimeQuote(stockCode);
    }

    /**
     * 转换为Yahoo Finance格式的股票代码
     * A股: 600519 -> 600519.SS (沪) / 000001 -> 000001.SZ (深)
     * 港股: hk00700 -> 0700.HK
     * 美股: AAPL -> AAPL
     * 日股: 7203 -> 7203.T
     */
    private String convertToYahooSymbol(String stockCode) {
        if (stockCode == null) return "";
        String code = stockCode.trim();
        
        // 已经是Yahoo格式
        if (code.contains(".")) return code;
        
        // 港股
        if (code.toLowerCase().startsWith("hk")) {
            String num = code.substring(2);
            // 去除前导0
            num = num.replaceFirst("^0+", "");
            return num + ".HK";
        }
        
        // 美股(全字母)
        if (code.matches("^[A-Za-z]+$")) {
            return code.toUpperCase();
        }
        
        // A股
        if (code.matches("^\\d{6}$")) {
            if (code.startsWith("6") || code.startsWith("9")) {
                return code + ".SS";
            } else {
                return code + ".SZ";
            }
        }
        
        return code;
    }

    private Double getDoubleOrNull(JsonNode array, int index) {
        if (!array.has(index) || array.get(index).isNull()) return null;
        return array.get(index).asDouble();
    }
}
