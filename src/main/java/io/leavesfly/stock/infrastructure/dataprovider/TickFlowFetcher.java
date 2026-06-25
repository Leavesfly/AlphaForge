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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * TickFlow数据源适配器
 *
 * TickFlow是一个第三方A股数据API服务
 * 
 * 接口:
 *   - 历史K线: {base_url}/api/v1/kline
 *   - 实时行情: {base_url}/api/v1/quote
 *   - 板块资金: {base_url}/api/v1/money_flow
 * 
 * 认证: 通过API Key进行身份验证
 */
@Component
public class TickFlowFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(TickFlowFetcher.class);
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TickFlowFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override public String getName() { return "tickflow"; }
    @Override public int getPriority() { return 1; } // 高优先级(配置后)
    @Override public boolean isAvailable() {
        return config.getTickflowApiKey() != null && !config.getTickflowApiKey().isEmpty()
                && config.getTickflowBaseUrl() != null && !config.getTickflowBaseUrl().isEmpty();
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String baseUrl = config.getTickflowBaseUrl();
            String url = baseUrl + "/api/v1/kline?" +
                    "code=" + stockCode +
                    "&start=" + startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                    "&end=" + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE) +
                    "&period=day" +
                    "&adjust=qfq";

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getTickflowApiKey())
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.debug("TickFlow K线请求失败: status={}", response.code());
                    return Collections.emptyList();
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.path("code").asInt() != 0) {
                    log.warn("TickFlow API错误: {}", root.path("message").asText());
                    return Collections.emptyList();
                }

                JsonNode data = root.path("data");
                if (!data.isArray()) return Collections.emptyList();

                List<StockDailyData> result = new ArrayList<>();
                for (JsonNode item : data) {
                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setStockName(item.path("name").asText(""));
                    d.setTradeDate(LocalDate.parse(item.path("date").asText()));
                    d.setOpenPrice(item.path("open").asDouble());
                    d.setHighPrice(item.path("high").asDouble());
                    d.setLowPrice(item.path("low").asDouble());
                    d.setClosePrice(item.path("close").asDouble());
                    d.setVolume(item.path("volume").asLong());
                    d.setAmount(item.path("amount").asDouble());
                    d.setChangePct(item.path("change_pct").asDouble());
                    d.setChangeAmount(item.path("change").asDouble());
                    d.setTurnoverRate(item.path("turnover_rate").asDouble());
                    d.setDataSource("tickflow");
                    result.add(d);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("TickFlow获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String baseUrl = config.getTickflowBaseUrl();
            String url = baseUrl + "/api/v1/quote?code=" + stockCode;

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getTickflowApiKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.path("code").asInt() != 0) return Collections.emptyMap();

                JsonNode data = root.path("data");
                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("stock_name", data.path("name").asText(""));
                quote.put("current_price", data.path("price").asDouble());
                quote.put("open_price", data.path("open").asDouble());
                quote.put("high_price", data.path("high").asDouble());
                quote.put("low_price", data.path("low").asDouble());
                quote.put("previous_close", data.path("prev_close").asDouble());
                quote.put("volume", data.path("volume").asLong());
                quote.put("amount", data.path("amount").asDouble());
                quote.put("change_pct", data.path("change_pct").asDouble());
                quote.put("change_amount", data.path("change").asDouble());
                quote.put("turnover_rate", data.path("turnover_rate").asDouble());
                quote.put("pe", data.path("pe").asDouble());
                quote.put("pb", data.path("pb").asDouble());
                quote.put("market_cap", data.path("market_cap").asDouble());
                return quote;
            }
        } catch (Exception e) {
            log.error("TickFlow实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        return getRealtimeQuote(stockCode);
    }

    /**
     * TickFlow特有功能: 获取板块资金流向
     */
    public Map<String, Object> getMoneyFlow(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String baseUrl = config.getTickflowBaseUrl();
            String url = baseUrl + "/api/v1/money_flow?code=" + stockCode;

            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + config.getTickflowApiKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.path("code").asInt() != 0) return Collections.emptyMap();

                JsonNode data = root.path("data");
                Map<String, Object> flow = new LinkedHashMap<>();
                flow.put("stock_code", stockCode);
                flow.put("main_net_inflow", data.path("main_net_inflow").asDouble());
                flow.put("retail_net_inflow", data.path("retail_net_inflow").asDouble());
                flow.put("super_large_inflow", data.path("super_large_inflow").asDouble());
                flow.put("large_inflow", data.path("large_inflow").asDouble());
                flow.put("medium_inflow", data.path("medium_inflow").asDouble());
                flow.put("small_inflow", data.path("small_inflow").asDouble());
                return flow;
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
