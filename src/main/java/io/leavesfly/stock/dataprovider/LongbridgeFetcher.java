package io.leavesfly.stock.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.model.entity.StockDailyData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Longbridge(长桥)数据源适配器
 * 
 * 对应Python版本的 longbridge_fetcher.py
 * 通过Longbridge OpenAPI获取港股/美股数据
 * 
 * API文档: https://open.longbridgeapp.com/docs
 * 认证: App Key + App Secret + Access Token
 * 接口:
 *   - 历史K线: /v1/quote/candlesticks
 *   - 实时行情: /v1/quote/realtime
 *   - 股票信息: /v1/quote/static_info
 */
@Component
public class LongbridgeFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(LongbridgeFetcher.class);
    private static final String BASE_URL = "https://openapi.longbridgeapp.com";

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LongbridgeFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override public String getName() { return "longbridge"; }
    @Override public int getPriority() { return 5; } // 港股/美股兜底
    @Override public boolean isAvailable() {
        return config.getLongbridgeAppKey() != null && !config.getLongbridgeAppKey().isEmpty()
                && config.getLongbridgeAccessToken() != null && !config.getLongbridgeAccessToken().isEmpty();
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String symbol = toLongbridgeSymbol(stockCode);
            // Longbridge K线接口
            String url = BASE_URL + "/v1/quote/candlesticks?" +
                    "symbol=" + symbol +
                    "&period=1000" + // 日K
                    "&count=200" +
                    "&adjust_type=0"; // 前复权

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getLongbridgeAccessToken())
                    .header("X-Api-Key", config.getLongbridgeAppKey())
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.debug("Longbridge K线请求失败: {}", response.code());
                    return Collections.emptyList();
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode candlesticks = root.path("data").path("candlesticks");
                if (!candlesticks.isArray()) return Collections.emptyList();

                List<StockDailyData> result = new ArrayList<>();
                for (JsonNode candle : candlesticks) {
                    long timestamp = candle.path("timestamp").asLong();
                    LocalDate date = Instant.ofEpochSecond(timestamp)
                            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
                    if (date.isBefore(startDate) || date.isAfter(endDate)) continue;

                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setTradeDate(date);
                    d.setOpenPrice(parsePrice(candle.path("open").asText()));
                    d.setHighPrice(parsePrice(candle.path("high").asText()));
                    d.setLowPrice(parsePrice(candle.path("low").asText()));
                    d.setClosePrice(parsePrice(candle.path("close").asText()));
                    d.setVolume(candle.path("volume").asLong());
                    d.setAmount(candle.path("turnover").asDouble());
                    d.setDataSource("longbridge");
                    result.add(d);
                }
                result.sort(Comparator.comparing(StockDailyData::getTradeDate));
                return result;
            }
        } catch (Exception e) {
            log.error("Longbridge获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String symbol = toLongbridgeSymbol(stockCode);
            String url = BASE_URL + "/v1/quote/realtime?symbol=" + symbol;

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getLongbridgeAccessToken())
                    .header("X-Api-Key", config.getLongbridgeAppKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                if (data.isMissingNode()) return Collections.emptyMap();

                // Longbridge返回数组格式
                JsonNode quote = data.isArray() && data.size() > 0 ? data.get(0) : data;

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stock_code", stockCode);
                result.put("stock_name", quote.path("symbol_name").asText(""));
                result.put("current_price", parsePrice(quote.path("last_done").asText()));
                result.put("open_price", parsePrice(quote.path("open").asText()));
                result.put("high_price", parsePrice(quote.path("high").asText()));
                result.put("low_price", parsePrice(quote.path("low").asText()));
                result.put("previous_close", parsePrice(quote.path("prev_close").asText()));
                result.put("volume", quote.path("volume").asLong());
                result.put("amount", quote.path("turnover").asDouble());

                double prev = parsePrice(quote.path("prev_close").asText());
                double cur = parsePrice(quote.path("last_done").asText());
                if (prev > 0) result.put("change_pct", (cur - prev) / prev * 100);
                return result;
            }
        } catch (Exception e) {
            log.error("Longbridge实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String symbol = toLongbridgeSymbol(stockCode);
            String url = BASE_URL + "/v1/quote/static_info?symbol=" + symbol;

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getLongbridgeAccessToken())
                    .header("X-Api-Key", config.getLongbridgeAppKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                JsonNode info = data.isArray() && data.size() > 0 ? data.get(0) : data;

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stock_code", stockCode);
                result.put("stock_name", info.path("name_cn").asText(info.path("name_en").asText("")));
                result.put("exchange", info.path("exchange").asText(""));
                result.put("currency", info.path("currency").asText(""));
                result.put("lot_size", info.path("lot_size").asInt());
                result.put("total_shares", info.path("total_shares").asLong());
                return result;
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 转换为Longbridge符号格式
     * 港股: 代码.HK (如 700.HK)
     * 美股: 代码.US (如 AAPL.US)
     */
    private String toLongbridgeSymbol(String stockCode) {
        String code = stockCode.toLowerCase();
        if (code.startsWith("hk")) {
            // hk00700 -> 700.HK
            String num = code.substring(2).replaceFirst("^0+", "");
            return num + ".HK";
        }
        if (code.matches("[a-zA-Z]+")) {
            // AAPL -> AAPL.US
            return stockCode.toUpperCase() + ".US";
        }
        // 默认当作A股(Longbridge也支持部分A股)
        return stockCode + ".SH";
    }

    /**
     * 解析Longbridge价格字符串(可能含小数)
     */
    private double parsePrice(String price) {
        if (price == null || price.isEmpty()) return 0;
        try { return Double.parseDouble(price); }
        catch (Exception e) { return 0; }
    }
}
