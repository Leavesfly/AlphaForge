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
 * BaoStock数据源适配器(Java版)
 * 
 * 对应Python版本的 baostock_fetcher.py
 * 原理: BaoStock提供HTTP API接口(baostock.com)获取历史K线
 * 注意: BaoStock官方是TCP协议，但也提供了HTTP查询接口
 * Java替代方案: 直接通过证券之星/新浪财经HTTP接口获取相同数据
 */
@Component
public class BaoStockFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(BaoStockFetcher.class);
    
    /** 使用新浪财经历史数据接口作为BaoStock替代 */
    private static final String SINA_HISTORY_URL = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var/CN_MarketDataService.getKLineData";
    /** 新浪实时行情 */
    private static final String SINA_REALTIME_URL = "https://hq.sinajs.cn/list=";
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BaoStockFetcher(AppConfig config) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override public String getName() { return "baostock"; }
    @Override public int getPriority() { return 4; }
    @Override public boolean isAvailable() { return true; }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String symbol = toSinaSymbol(stockCode);
            // 使用新浪财经K线接口
            String url = SINA_HISTORY_URL + "?" +
                    "symbol=" + symbol +
                    "&scale=240" + // 日K(240分钟)
                    "&ma=no" +
                    "&datalen=" + (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 10);

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                String body = response.body().string();
                
                // 解析JSONP响应
                int jsonStart = body.indexOf("[");
                int jsonEnd = body.lastIndexOf("]");
                if (jsonStart < 0 || jsonEnd < 0) return Collections.emptyList();
                String json = body.substring(jsonStart, jsonEnd + 1);
                
                JsonNode array = objectMapper.readTree(json);
                List<StockDailyData> result = new ArrayList<>();
                
                for (JsonNode item : array) {
                    LocalDate date = LocalDate.parse(item.path("day").asText().substring(0, 10));
                    if (date.isBefore(startDate) || date.isAfter(endDate)) continue;
                    
                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setTradeDate(date);
                    d.setOpenPrice(item.path("open").asDouble());
                    d.setHighPrice(item.path("high").asDouble());
                    d.setLowPrice(item.path("low").asDouble());
                    d.setClosePrice(item.path("close").asDouble());
                    d.setVolume(item.path("volume").asLong());
                    d.setDataSource("baostock");
                    result.add(d);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("BaoStock获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String symbol = toSinaSymbol(stockCode);
            String url = SINA_REALTIME_URL + symbol;
            
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                String body = response.body().string();
                return parseSinaQuote(stockCode, body);
            }
        } catch (Exception e) {
            log.error("BaoStock实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) { return getRealtimeQuote(stockCode); }

    /**
     * 解析新浪实时行情
     * 格式: var hq_str_sh600519="贵州茅台,1851.01,1849.00,1862.99,...";
     */
    private Map<String, Object> parseSinaQuote(String stockCode, String body) {
        Map<String, Object> quote = new LinkedHashMap<>();
        int start = body.indexOf("\"");
        int end = body.lastIndexOf("\"");
        if (start < 0 || end <= start) return quote;
        
        String[] fields = body.substring(start + 1, end).split(",");
        if (fields.length < 32) return quote;

        quote.put("stock_code", stockCode);
        quote.put("stock_name", fields[0]);
        quote.put("open_price", parseDouble(fields[1]));
        quote.put("previous_close", parseDouble(fields[2]));
        quote.put("current_price", parseDouble(fields[3]));
        quote.put("high_price", parseDouble(fields[4]));
        quote.put("low_price", parseDouble(fields[5]));
        quote.put("volume", parseLong(fields[8]));
        quote.put("amount", parseDouble(fields[9]));
        
        double prev = parseDouble(fields[2]);
        double cur = parseDouble(fields[3]);
        if (prev > 0) quote.put("change_pct", (cur - prev) / prev * 100);
        return quote;
    }

    private String toSinaSymbol(String code) {
        code = code.replaceAll("^(sh|sz|SH|SZ)", "");
        if (code.startsWith("6") || code.startsWith("9")) return "sh" + code;
        return "sz" + code;
    }

    private double parseDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
}
