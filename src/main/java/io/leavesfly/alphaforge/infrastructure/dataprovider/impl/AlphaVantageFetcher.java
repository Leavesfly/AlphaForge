package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.infrastructure.dataprovider.BaseDataFetcher;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Alpha Vantage数据源适配器
 * 获取全球市场数据(美股为主)
 */
@Component
public class AlphaVantageFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageFetcher.class);
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AlphaVantageFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
    }

    @Override public String getName() { return "alphavantage"; }
    @Override public int getPriority() { return 7; }
    @Override public boolean isAvailable() {
        return config.getAlphavantageApiKey() != null && !config.getAlphavantageApiKey().isEmpty();
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            String url = BASE_URL + "?function=TIME_SERIES_DAILY_ADJUSTED&symbol=" + stockCode.toUpperCase() +
                    "&outputsize=full&apikey=" + config.getAlphavantageApiKey();
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode timeSeries = root.path("Time Series (Daily)");
                if (timeSeries.isMissingNode()) return Collections.emptyList();

                List<StockDailyData> dataList = new ArrayList<>();
                Iterator<String> dates = timeSeries.fieldNames();
                while (dates.hasNext()) {
                    String dateStr = dates.next();
                    LocalDate date = LocalDate.parse(dateStr);
                    if (date.isBefore(startDate) || date.isAfter(endDate)) continue;

                    JsonNode day = timeSeries.get(dateStr);
                    StockDailyData data = new StockDailyData();
                    data.setStockCode(stockCode);
                    data.setTradeDate(date);
                    data.setOpenPrice(day.path("1. open").asDouble());
                    data.setHighPrice(day.path("2. high").asDouble());
                    data.setLowPrice(day.path("3. low").asDouble());
                    data.setClosePrice(day.path("5. adjusted close").asDouble());
                    data.setVolume(day.path("6. volume").asLong());
                    data.setDataSource("alphavantage");
                    dataList.add(data);
                }
                dataList.sort(Comparator.comparing(StockDailyData::getTradeDate));
                return dataList;
            }
        } catch (Exception e) {
            log.error("AlphaVantage获取数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + stockCode.toUpperCase() +
                    "&apikey=" + config.getAlphavantageApiKey();
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode quote = objectMapper.readTree(response.body().string()).path("Global Quote");
                if (quote.isMissingNode()) return Collections.emptyMap();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("stock_code", stockCode);
                result.put("current_price", quote.path("05. price").asDouble());
                result.put("open_price", quote.path("02. open").asDouble());
                result.put("high_price", quote.path("03. high").asDouble());
                result.put("low_price", quote.path("04. low").asDouble());
                result.put("volume", quote.path("06. volume").asLong());
                result.put("change_pct", Double.parseDouble(quote.path("10. change percent").asText("0").replace("%", "")));
                return result;
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        return getRealtimeQuote(stockCode);
    }
}
