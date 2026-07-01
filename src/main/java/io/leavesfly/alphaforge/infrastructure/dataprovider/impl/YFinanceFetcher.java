package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.AdjustType;
import io.leavesfly.alphaforge.domain.model.enums.KLineFrequency;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.util.StockCodeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.infrastructure.dataprovider.BaseDataFetcher;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Yahoo Finance数据源适配器
 *
 * 用于获取美股、港股、日股等国际市场数据
 */
@Component
public class YFinanceFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(YFinanceFetcher.class);
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YFinanceFetcher(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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

    /** Yahoo Finance 不封IP，限流间隔 200ms */
    @Override public long getRateLimitMs() { return 200; }

    /** Yahoo 覆盖港美股/日韩，不支持A股 */
    @Override public Set<MarketType> getSupportedMarkets() {
        return Set.of(MarketType.HK, MarketType.US, MarketType.JP, MarketType.KR);
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        return getHistoryData(stockCode, startDate, endDate, KLineFrequency.DAILY, AdjustType.FRONT);
    }

    /**
     * 多频率K线获取 — Yahoo Finance chart API
     * interval映射: 1m/5m/15m/1h/1d/1wk/1mo
     * adjust: FRONT通过events=div,splits自动前复权，NONE不传events
     */
    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate,
                                                  KLineFrequency frequency, AdjustType adjust) {
        try {
            String symbol = convertToYahooSymbol(stockCode);
            long period1 = startDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            long period2 = endDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            String interval = frequencyToYahooInterval(frequency);
            String events = (adjust == AdjustType.NONE) ? "" : "&events=div,splits";

            String url = YAHOO_CHART_URL + symbol + "?" +
                    "period1=" + period1 +
                    "&period2=" + period2 +
                    "&interval=" + interval +
                    events;

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

                ZoneId tz = getSymbolTimezone(symbol);
                for (int i = 0; i < timestamps.size(); i++) {
                    StockDailyData data = new StockDailyData();
                    data.setStockCode(stockCode);
                    long ts = timestamps.get(i).asLong();
                    data.setTradeDate(Instant.ofEpochSecond(ts).atZone(tz).toLocalDate());
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
            if (StockCodeUtils.isSSE(code)) {
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

    /**
     * 根据Yahoo symbol后缀返回对应交易所时区
     * .SS/.SZ → Asia/Shanghai, .HK → Asia/Hong_Kong, .T → Asia/Tokyo, .KS → Asia/Seoul, 无后缀(美股) → America/New_York
     */
    private ZoneId getSymbolTimezone(String symbol) {
        if (symbol == null || symbol.isEmpty()) return ZoneId.of("America/New_York");
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".SS") || upper.endsWith(".SZ")) return ZoneId.of("Asia/Shanghai");
        if (upper.endsWith(".HK")) return ZoneId.of("Asia/Hong_Kong");
        if (upper.endsWith(".T")) return ZoneId.of("Asia/Tokyo");
        if (upper.endsWith(".KS")) return ZoneId.of("Asia/Seoul");
        return ZoneId.of("America/New_York");
    }

    /** KLineFrequency → Yahoo interval 参数 */
    private String frequencyToYahooInterval(KLineFrequency freq) {
        return switch (freq) {
            case MINUTE_1 -> "1m";
            case MINUTE_5 -> "5m";
            case MINUTE_15 -> "15m";
            case MINUTE_30 -> "30m";
            case MINUTE_60 -> "1h";
            case DAILY -> "1d";
            case WEEKLY -> "1wk";
            case MONTHLY -> "1mo";
        };
    }
}
