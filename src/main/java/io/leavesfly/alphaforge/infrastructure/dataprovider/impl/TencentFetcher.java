package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.infrastructure.dataprovider.BaseDataFetcher;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯财经数据源适配器
 * 通过腾讯股票接口获取A股/港股实时和历史数据
 */
@Component
public class TencentFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(TencentFetcher.class);
    private static final String REALTIME_URL = "https://qt.gtimg.cn/q=";
    private static final String HISTORY_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get";
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TencentFetcher(AppConfig config) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build();
    }

    @Override public String getName() { return "tencent"; }
    @Override public int getPriority() { return 3; }
    @Override public boolean isAvailable() { return true; }

    /** 腾讯财经不封IP，限流间隔 100ms */
    @Override public long getRateLimitMs() { return 100; }

    /** 腾讯支持A股和港股 */
    @Override public Set<MarketType> getSupportedMarkets() { return Set.of(MarketType.A, MarketType.HK); }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String symbol = convertToTencentSymbol(stockCode);
            String start = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String end = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = HISTORY_URL + "?param=" + symbol + ",day," + start + "," + end + ",640,qfq";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode data = root.path("data").path(symbol);
                JsonNode dayData = data.has("day") ? data.get("day") : data.get("qfqday");
                if (dayData == null || !dayData.isArray()) return Collections.emptyList();

                List<StockDailyData> result = new ArrayList<>();
                for (JsonNode row : dayData) {
                    if (row.size() < 6) continue;
                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setTradeDate(LocalDate.parse(row.get(0).asText()));
                    d.setOpenPrice(row.get(1).asDouble());
                    d.setClosePrice(row.get(2).asDouble());
                    d.setHighPrice(row.get(3).asDouble());
                    d.setLowPrice(row.get(4).asDouble());
                    d.setVolume(row.get(5).asLong());
                    d.setDataSource("tencent");
                    result.add(d);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("腾讯数据源获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String symbol = convertToTencentSymbol(stockCode);
            String url = REALTIME_URL + symbol;
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                String body = response.body().string();
                return parseTencentRealtimeResponse(stockCode, body);
            }
        } catch (Exception e) {
            log.error("腾讯实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) { return getRealtimeQuote(stockCode); }

    /**
     * 解析腾讯实时行情响应
     * 格式: v_sh600519="1~贵州茅台~600519~1849.00~..."
     */
    private Map<String, Object> parseTencentRealtimeResponse(String stockCode, String body) {
        Map<String, Object> quote = new LinkedHashMap<>();
        int startIdx = body.indexOf("\"");
        int endIdx = body.lastIndexOf("\"");
        if (startIdx < 0 || endIdx <= startIdx) return quote;

        String data = body.substring(startIdx + 1, endIdx);
        String[] fields = data.split("~");
        if (fields.length < 35) return quote;

        quote.put("stock_code", stockCode);
        quote.put("stock_name", fields[1]);
        quote.put("current_price", parseDoubleSafe(fields[3]));
        quote.put("previous_close", parseDoubleSafe(fields[4]));
        quote.put("open_price", parseDoubleSafe(fields[5]));
        quote.put("volume", parseLongSafe(fields[6]));
        quote.put("buy_volume", parseLongSafe(fields[7]));
        quote.put("sell_volume", parseLongSafe(fields[8]));
        quote.put("high_price", parseDoubleSafe(fields[33]));
        quote.put("low_price", parseDoubleSafe(fields[34]));
        double prev = parseDoubleSafe(fields[4]);
        double cur = parseDoubleSafe(fields[3]);
        if (prev > 0) quote.put("change_pct", (cur - prev) / prev * 100);
        return quote;
    }

    /**
     * 获取分钟级K线数据
     * 通过腾讯分时K线接口获取
     *
     * @param stockCode 股票代码
     * @param period    周期(1/5/15/30/60分钟)
     * @param count     数据条数
     * @return K线数据列表
     */
    @Override
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        try {
            String symbol = convertToTencentSymbol(stockCode);
            // 腾讯分钟K线类型: 1=1分钟, 5=5分钟, 15=15分钟, 30=30分钟, 60=60分钟
            String minuteType = period + "min";
            String url = HISTORY_URL + "?param=" + symbol + "," + minuteType + ",,,," + count + ",qfq";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode data = root.path("data").path(symbol);

                // 腾讯分钟数据字段名取决于周期
                String dataKey = minuteType;
                JsonNode minuteData = data.has(dataKey) ? data.get(dataKey) : data.get("qfq" + dataKey);
                if (minuteData == null || !minuteData.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode row : minuteData) {
                    if (row.size() < 6) continue;
                    Map<String, Object> bar = new LinkedHashMap<>();
                    bar.put("time", row.get(0).asText(""));
                    bar.put("open", row.get(1).asDouble());
                    bar.put("close", row.get(2).asDouble());
                    bar.put("high", row.get(3).asDouble());
                    bar.put("low", row.get(4).asDouble());
                    bar.put("volume", row.get(5).asLong());
                    result.add(bar);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("腾讯分钟数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String convertToTencentSymbol(String stockCode) {
        String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
        if (code.toLowerCase().startsWith("hk")) return "hk" + code.substring(2);
        if (code.startsWith("6") || code.startsWith("9")) return "sh" + code;
        return "sz" + code;
    }

    private double parseDoubleSafe(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private long parseLongSafe(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
}
