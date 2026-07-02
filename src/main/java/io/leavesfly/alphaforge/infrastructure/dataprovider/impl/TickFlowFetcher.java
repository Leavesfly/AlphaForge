package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.config.DataProviderConfig;
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
 * TickFlow 数据源适配器
 *
 * 通过 TickFlow REST API 获取 A 股、港股、美股等市场的实时行情、K 线数据和财务数据。
 * TickFlow 提供统一的 代码.市场后缀 格式（如 600000.SH、AAPL.US、00700.HK），
 * 支持多市场混合查询、多频率 K 线（分钟/日/周/月）及前/后复权。
 *
 * API 文档: https://tickflow.org
 * 认证方式: x-api-key 请求头
 */
@Component
public class TickFlowFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(TickFlowFetcher.class);

    /** K 线接口 */
    private static final String KLINES_PATH = "/v1/klines";
    /** 批量 K 线接口 */
    private static final String KLINES_BATCH_PATH = "/v1/klines/batch";
    /** 日内分时 K 线接口 */
    private static final String KLINES_INTRADAY_PATH = "/v1/klines/intraday";
    /** 实时行情接口 */
    private static final String QUOTES_PATH = "/v1/quotes";
    /** 标的信息接口 */
    private static final String INSTRUMENTS_PATH = "/v1/instruments";
    /** 财务数据接口前缀 */
    private static final String FINANCIALS_PREFIX = "/v1/financials/";

    /** 行情接口 GET 请求的最大标的数 */
    private static final int QUOTES_GET_MAX_SYMBOLS = 20;

    private final DataProviderConfig dataProviderConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TickFlowFetcher(DataProviderConfig dataProviderConfig,
                           OkHttpClient httpClient,
                           ObjectMapper objectMapper) {
        this.dataProviderConfig = dataProviderConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "tickflow";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        String apiKey = dataProviderConfig.getTickflowApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    /** TickFlow 是专业 API 服务，不封 IP，限流间隔 200ms */
    @Override
    public long getRateLimitMs() {
        return 200;
    }

    /** TickFlow 支持 A 股、港股、美股 */
    @Override
    public Set<MarketType> getSupportedMarkets() {
        return Set.of(MarketType.A, MarketType.HK, MarketType.US);
    }

    // ========== 历史K线数据 ==========

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        return getHistoryData(stockCode, startDate, endDate, KLineFrequency.DAILY, AdjustType.FRONT);
    }

    /**
     * 多频率 K 线获取 — TickFlow /v1/klines 接口
     *
     * TickFlow 返回紧凑列式数据格式:
     * {"data": {"timestamp": [ms...], "open": [...], "high": [...], "low": [...],
     *           "close": [...], "volume": [...], "amount": [...]}}
     *
     * period 映射: 1m/5m/15m/30m/60m/1d/1w/1M
     * adjust 映射: forward(前复权)/backward(后复权)/none(不复权)
     */
    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate,
                                               KLineFrequency frequency, AdjustType adjust) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String period = frequencyToPeriod(frequency);
            String adjustStr = adjustToTickFlow(adjust);

            // 计算请求条数：日频按天数，分钟频适当放大
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            int count = (int) Math.min(daysBetween + 10, 10000);

            String url = dataProviderConfig.getTickflowBaseUrl() + KLINES_PATH +
                    "?symbol=" + symbol +
                    "&period=" + period +
                    "&count=" + count +
                    "&adjust=" + adjustStr;

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyList();

            JsonNode klineData = data.path("data");
            if (klineData.isMissingNode()) return Collections.emptyList();

            return parseCompactKlines(klineData, stockCode, symbol, frequency);
        } catch (Exception e) {
            log.error("TickFlow获取历史K线失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取日内分钟级 K 线数据
     * 通过 TickFlow /v1/klines/intraday 接口获取当日分时数据
     */
    @Override
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String periodStr = period + "m";

            String url = dataProviderConfig.getTickflowBaseUrl() + KLINES_INTRADAY_PATH +
                    "?symbol=" + symbol +
                    "&period=" + periodStr;
            if (count > 0) {
                url += "&count=" + count;
            }

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyList();

            JsonNode klineData = data.path("data");
            if (klineData.isMissingNode()) return Collections.emptyList();

            return parseCompactKlinesAsMaps(klineData);
        } catch (Exception e) {
            log.error("TickFlow获取分钟数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 实时行情数据 ==========

    /**
     * 获取实时行情 — TickFlow /v1/quotes 接口
     * 返回包含 symbol, name, last_price, prev_close, open, high, low, volume, amount, ext 等字段
     */
    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String url = dataProviderConfig.getTickflowBaseUrl() + QUOTES_PATH +
                    "?symbols=" + symbol;

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyMap();

            JsonNode quotesArray = data.path("data");
            if (!quotesArray.isArray() || quotesArray.isEmpty()) return Collections.emptyMap();

            JsonNode q = quotesArray.get(0);
            return parseQuote(q, stockCode);
        } catch (Exception e) {
            log.error("TickFlow获取实时行情失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 批量获取实时行情 — TickFlow /v1/quotes 接口
     * 小于等于 20 个标的用 GET，超过用 POST
     */
    @Override
    public Map<String, Map<String, Object>> getBatchRealtimeQuotes(List<String> stockCodes) {
        if (!isAvailable() || stockCodes == null || stockCodes.isEmpty()) return Collections.emptyMap();
        try {
            List<String> symbols = stockCodes.stream()
                    .map(StockCodeUtils::toTickFlowSymbol)
                    .toList();

            JsonNode quotesArray;
            if (symbols.size() <= QUOTES_GET_MAX_SYMBOLS) {
                String url = dataProviderConfig.getTickflowBaseUrl() + QUOTES_PATH +
                        "?symbols=" + String.join(",", symbols);
                JsonNode data = executeGet(url);
                if (data == null) return Collections.emptyMap();
                quotesArray = data.path("data");
            } else {
                // 大批量用 POST
                String jsonBody = objectMapper.writeValueAsString(Map.of("symbols", symbols));
                JsonNode data = executePost(dataProviderConfig.getTickflowBaseUrl() + QUOTES_PATH, jsonBody);
                if (data == null) return Collections.emptyMap();
                quotesArray = data.path("data");
            }

            if (!quotesArray.isArray()) return Collections.emptyMap();

            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (JsonNode q : quotesArray) {
                String symbol = q.path("symbol").asText("");
                // 映射回原始 stockCode
                String originalCode = mapSymbolBack(stockCodes, symbol);
                result.put(originalCode, parseQuote(q, originalCode));
            }
            return result;
        } catch (Exception e) {
            log.error("TickFlow批量获取行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String url = dataProviderConfig.getTickflowBaseUrl() + INSTRUMENTS_PATH +
                    "?symbols=" + symbol;

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyMap();

            JsonNode instArray = data.path("data");
            if (!instArray.isArray() || instArray.isEmpty()) return Collections.emptyMap();

            JsonNode inst = instArray.get(0);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("stock_code", stockCode);
            info.put("symbol", inst.path("symbol").asText(""));
            info.put("stock_name", inst.path("name").asText(""));
            info.put("code", inst.path("code").asText(""));
            info.put("exchange", inst.path("exchange").asText(""));
            info.put("region", inst.path("region").asText(""));
            info.put("instrument_type", inst.path("instrument_type").asText(""));
            return info;
        } catch (Exception e) {
            log.error("TickFlow获取标的信息失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ========== 基本面数据 ==========

    /**
     * 获取财报三表 — TickFlow /v1/financials/* 接口
     * statementType: "income" / "balance" / "cashflow"
     */
    @Override
    public List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String endpoint = switch (statementType.toLowerCase()) {
                case "balance" -> "balance-sheet";
                case "cashflow" -> "cash-flow";
                default -> "income";
            };

            String url = dataProviderConfig.getTickflowBaseUrl() + FINANCIALS_PREFIX + endpoint +
                    "?symbols=" + symbol;

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyList();

            JsonNode symbolData = data.path("data").path(symbol);
            if (!symbolData.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : symbolData) {
                Map<String, Object> row = new LinkedHashMap<>();
                item.fields().forEachRemaining(entry -> row.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText()));
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.error("TickFlow获取财报失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取关键财务指标 — TickFlow /v1/financials/metrics 接口
     * 包含 ROE/ROA/EPS/毛利率/资产负债率等
     */
    @Override
    public List<Map<String, Object>> getKeyIndicators(String stockCode) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String symbol = StockCodeUtils.toTickFlowSymbol(stockCode);
            String url = dataProviderConfig.getTickflowBaseUrl() + FINANCIALS_PREFIX + "metrics" +
                    "?symbols=" + symbol;

            JsonNode data = executeGet(url);
            if (data == null) return Collections.emptyList();

            JsonNode symbolData = data.path("data").path(symbol);
            if (!symbolData.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : symbolData) {
                Map<String, Object> row = new LinkedHashMap<>();
                item.fields().forEachRemaining(entry -> {
                    JsonNode val = entry.getValue();
                    if (val.isNumber()) {
                        row.put(entry.getKey(), val.asDouble());
                    } else if (val.isTextual()) {
                        row.put(entry.getKey(), val.asText());
                    } else if (val.isBoolean()) {
                        row.put(entry.getKey(), val.asBoolean());
                    }
                });
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.error("TickFlow获取关键指标失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 内部工具方法 ==========

    /**
     * 执行 GET 请求并返回解析后的 JSON
     */
    private JsonNode executeGet(String url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("x-api-key", dataProviderConfig.getTickflowApiKey())
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("TickFlow API请求失败: {} - HTTP {}", url, response.code());
                return null;
            }
            return objectMapper.readTree(response.body().string());
        }
    }

    /**
     * 执行 POST 请求并返回解析后的 JSON
     */
    private JsonNode executePost(String url, String jsonBody) throws Exception {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
        Request request = new Request.Builder().url(url)
                .header("x-api-key", dataProviderConfig.getTickflowApiKey())
                .header("Accept", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("TickFlow API请求失败: {} - HTTP {}", url, response.code());
                return null;
            }
            return objectMapper.readTree(response.body().string());
        }
    }

    /**
     * 解析 TickFlow 紧凑列式 K 线数据为 StockDailyData 列表
     *
     * TickFlow 返回格式: {timestamp: [ms...], open: [...], high: [...], low: [...],
     *                    close: [...], volume: [...], amount: [...]}
     */
    private List<StockDailyData> parseCompactKlines(JsonNode klineData, String stockCode,
                                                     String symbol, KLineFrequency frequency) {
        JsonNode timestamps = klineData.path("timestamp");
        if (!timestamps.isArray() || timestamps.isEmpty()) return Collections.emptyList();

        JsonNode opens = klineData.path("open");
        JsonNode highs = klineData.path("high");
        JsonNode lows = klineData.path("low");
        JsonNode closes = klineData.path("close");
        JsonNode volumes = klineData.path("volume");
        JsonNode amounts = klineData.path("amount");

        ZoneId zoneId = getSymbolTimezone(symbol);
        boolean isDaily = !frequency.isMinuteLevel();

        List<StockDailyData> result = new ArrayList<>();
        Double prevClose = null;

        for (int i = 0; i < timestamps.size(); i++) {
            StockDailyData d = new StockDailyData();
            d.setStockCode(stockCode);
            d.setDataSource("tickflow");

            long ts = timestamps.get(i).asLong();
            d.setTradeDate(Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate());

            d.setOpenPrice(getDoubleOrNull(opens, i));
            d.setHighPrice(getDoubleOrNull(highs, i));
            d.setLowPrice(getDoubleOrNull(lows, i));
            d.setClosePrice(getDoubleOrNull(closes, i));
            d.setVolume(volumes.has(i) && !volumes.get(i).isNull() ? volumes.get(i).asLong(0) : 0L);
            d.setAmount(getDoubleOrNull(amounts, i));

            // 计算涨跌幅
            Double close = d.getClosePrice();
            if (prevClose != null && prevClose > 0 && close != null) {
                d.setChangePct((close - prevClose) / prevClose * 100);
                d.setChangeAmount(close - prevClose);
            }
            prevClose = close;

            result.add(d);
        }

        // 日频以上数据按日期过滤
        if (isDaily && !result.isEmpty()) {
            // TickFlow 按 count 返回最近 N 条，这里不做额外过滤
                // 如果需要按 startDate/endDate 过滤，可在调用方处理
        }

        return result;
    }

    /**
     * 解析 TickFlow 紧凑列式 K 线数据为 Map 列表（用于分钟数据）
     */
    private List<Map<String, Object>> parseCompactKlinesAsMaps(JsonNode klineData) {
        JsonNode timestamps = klineData.path("timestamp");
        if (!timestamps.isArray() || timestamps.isEmpty()) return Collections.emptyList();

        JsonNode opens = klineData.path("open");
        JsonNode highs = klineData.path("high");
        JsonNode lows = klineData.path("low");
        JsonNode closes = klineData.path("close");
        JsonNode volumes = klineData.path("volume");
        JsonNode amounts = klineData.path("amount");

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            Map<String, Object> bar = new LinkedHashMap<>();
            long ts = timestamps.get(i).asLong();
            bar.put("timestamp", ts);
            bar.put("time", Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toString());
            bar.put("open", getDoubleOrNull(opens, i));
            bar.put("high", getDoubleOrNull(highs, i));
            bar.put("low", getDoubleOrNull(lows, i));
            bar.put("close", getDoubleOrNull(closes, i));
            bar.put("volume", volumes.has(i) && !volumes.get(i).isNull() ? volumes.get(i).asLong(0) : 0L);
            bar.put("amount", getDoubleOrNull(amounts, i));
            result.add(bar);
        }
        return result;
    }

    /**
     * 解析 TickFlow 行情数据为标准 Map
     */
    private Map<String, Object> parseQuote(JsonNode q, String stockCode) {
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("stock_code", stockCode);
        quote.put("stock_name", q.path("name").asText(""));
        quote.put("current_price", q.path("last_price").asDouble(0));
        quote.put("previous_close", q.path("prev_close").asDouble(0));
        quote.put("open_price", q.path("open").asDouble(0));
        quote.put("high_price", q.path("high").asDouble(0));
        quote.put("low_price", q.path("low").asDouble(0));
        quote.put("volume", q.path("volume").asLong(0));
        quote.put("amount", q.path("amount").asDouble(0));

        double lastPrice = q.path("last_price").asDouble(0);
        double prevClose = q.path("prev_close").asDouble(0);
        if (prevClose > 0) {
            quote.put("change_pct", (lastPrice - prevClose) / prevClose * 100);
            quote.put("change_amount", lastPrice - prevClose);
        }

        // 解析扩展字段
        JsonNode ext = q.path("ext");
        if (!ext.isMissingNode() && ext.isObject()) {
            quote.put("ext", ext.toString());
            // 提取常用扩展字段
            if (ext.has("change_pct")) quote.put("ext_change_pct", ext.path("change_pct").asDouble(0));
            if (ext.has("turnover_rate")) quote.put("turnover_rate", ext.path("turnover_rate").asDouble(0));
            if (ext.has("pe")) quote.put("pe", ext.path("pe").asDouble(0));
            if (ext.has("market_cap")) quote.put("market_cap", ext.path("market_cap").asDouble(0));
            if (ext.has("limit_up")) quote.put("limit_up", ext.path("limit_up").asDouble(0));
            if (ext.has("limit_down")) quote.put("limit_down", ext.path("limit_down").asDouble(0));
            if (ext.has("volume_ratio")) quote.put("volume_ratio", ext.path("volume_ratio").asDouble(0));
            if (ext.has("amplitude")) quote.put("amplitude", ext.path("amplitude").asDouble(0));
        }

        return quote;
    }

    /**
     * KLineFrequency → TickFlow period 参数
     */
    private String frequencyToPeriod(KLineFrequency freq) {
        return switch (freq) {
            case MINUTE_1 -> "1m";
            case MINUTE_5 -> "5m";
            case MINUTE_15 -> "15m";
            case MINUTE_30 -> "30m";
            case MINUTE_60 -> "60m";
            case DAILY -> "1d";
            case WEEKLY -> "1w";
            case MONTHLY -> "1M";
        };
    }

    /**
     * AdjustType → TickFlow adjust 参数
     */
    private String adjustToTickFlow(AdjustType adjust) {
        return switch (adjust) {
            case NONE -> "none";
            case FRONT -> "forward";
            case BACK -> "backward";
        };
    }

    /**
     * 根据 TickFlow symbol 后缀返回对应交易所时区
     */
    private ZoneId getSymbolTimezone(String symbol) {
        if (symbol == null || symbol.isEmpty()) return ZoneId.of("Asia/Shanghai");
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".SH") || upper.endsWith(".SZ") || upper.endsWith(".BJ")) {
            return ZoneId.of("Asia/Shanghai");
        }
        if (upper.endsWith(".HK")) return ZoneId.of("Asia/Hong_Kong");
        if (upper.endsWith(".US")) return ZoneId.of("America/New_York");
        return ZoneId.of("Asia/Shanghai");
    }

    private Double getDoubleOrNull(JsonNode array, int index) {
        if (!array.has(index) || array.get(index).isNull()) return null;
        return array.get(index).asDouble();
    }

    /**
     * 将 TickFlow symbol 映射回原始 stockCode
     * 通过查找 stockCodes 列表中转换为该 symbol 的原始代码
     */
    private String mapSymbolBack(List<String> stockCodes, String symbol) {
        for (String code : stockCodes) {
            if (StockCodeUtils.toTickFlowSymbol(code).equalsIgnoreCase(symbol)) {
                return code;
            }
        }
        return symbol;
    }
}
