package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.config.DataProviderConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.util.StockCodeUtils;
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

/**
 * Tushare数据源适配器
 * 通过Tushare Pro API获取A股数据
 */
@Component
public class TushareFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(TushareFetcher.class);
    private static final String TUSHARE_API_URL = "http://api.tushare.pro";
    private final DataProviderConfig dataProviderConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TushareFetcher(DataProviderConfig dataProviderConfig, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.dataProviderConfig = dataProviderConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override public String getName() { return "tushare"; }
    @Override public int getPriority() { return 2; }
    @Override public boolean isAvailable() {
        return dataProviderConfig.getTushareToken() != null && !dataProviderConfig.getTushareToken().isEmpty();
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String tsCode = convertToTsCode(stockCode);
            String startStr = startDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            String endStr = endDate.format(DateTimeFormatter.BASIC_ISO_DATE);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", tsCode);
            params.put("start_date", startStr);
            params.put("end_date", endStr);
            params.put("adj", "qfq");

            JsonNode result = callTushareApi("daily", params,
                    "ts_code,trade_date,open,high,low,close,vol,amount,pct_chg,change,turnover_rate");

            if (result == null || !result.has("items")) return Collections.emptyList();

            List<StockDailyData> dataList = new ArrayList<>();
            for (JsonNode row : result.get("items")) {
                StockDailyData data = new StockDailyData();
                data.setStockCode(stockCode);
                data.setTradeDate(LocalDate.parse(row.get(1).asText(), DateTimeFormatter.BASIC_ISO_DATE));
                data.setOpenPrice(row.get(2).asDouble());
                data.setHighPrice(row.get(3).asDouble());
                data.setLowPrice(row.get(4).asDouble());
                data.setClosePrice(row.get(5).asDouble());
                data.setVolume(row.get(6).asLong());
                data.setAmount(row.get(7).asDouble() * 1000); // 千元->元
                data.setChangePct(row.get(8).asDouble());
                data.setChangeAmount(row.get(9).asDouble());
                data.setTurnoverRate(row.get(10).asDouble());
                data.setDataSource("tushare");
                dataList.add(data);
            }
            Collections.reverse(dataList); // Tushare返回倒序
            return dataList;
        } catch (Exception e) {
            log.error("Tushare获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        // Tushare实时行情需要更高权限，使用日线最新数据代替
        List<StockDailyData> data = getHistoryData(stockCode, LocalDate.now().minusDays(5), LocalDate.now());
        if (data.isEmpty()) return Collections.emptyMap();
        StockDailyData latest = data.get(data.size() - 1);
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("stock_code", stockCode);
        quote.put("current_price", latest.getClosePrice());
        quote.put("change_pct", latest.getChangePct());
        quote.put("volume", latest.getVolume());
        return quote;
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            Map<String, Object> params = Map.of("ts_code", convertToTsCode(stockCode));
            JsonNode result = callTushareApi("stock_basic", params, "ts_code,name,area,industry,market,list_date");
            if (result != null && result.has("items") && result.get("items").size() > 0) {
                JsonNode row = result.get("items").get(0);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("stock_code", stockCode);
                info.put("stock_name", row.get(1).asText());
                info.put("area", row.get(2).asText());
                info.put("industry", row.get(3).asText());
                info.put("market", row.get(4).asText());
                info.put("list_date", row.get(5).asText());
                return info;
            }
        } catch (Exception e) {
            log.error("Tushare获取股票信息失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private JsonNode callTushareApi(String apiName, Map<String, Object> params, String fields) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_name", apiName);
        body.put("token", dataProviderConfig.getTushareToken());
        body.put("params", params);
        body.put("fields", fields);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body), MediaType.get("application/json"));
        Request request = new Request.Builder().url(TUSHARE_API_URL).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JsonNode root = objectMapper.readTree(response.body().string());
            if (root.path("code").asInt() != 0) {
                log.warn("Tushare API错误: {}", root.path("msg").asText());
                return null;
            }
            return root.path("data");
        }
    }

    private String convertToTsCode(String stockCode) {
        return StockCodeUtils.toTsCode(stockCode);
    }
}
