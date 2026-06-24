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
 * Finnhub数据源适配器
 * 对应Python版本的 finnhub_fetcher.py
 * 获取美股/全球市场数据
 */
@Component
public class FinnhubFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(FinnhubFetcher.class);
    private static final String BASE_URL = "https://finnhub.io/api/v1";
    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FinnhubFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build();
    }

    @Override public String getName() { return "finnhub"; }
    @Override public int getPriority() { return 6; }
    @Override public boolean isAvailable() {
        return config.getFinnhubApiKey() != null && !config.getFinnhubApiKey().isEmpty();
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (!isAvailable()) return Collections.emptyList();
        try {
            long from = startDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            long to = endDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
            String symbol = stockCode.toUpperCase();
            String url = BASE_URL + "/stock/candle?symbol=" + symbol +
                    "&resolution=D&from=" + from + "&to=" + to + "&token=" + config.getFinnhubApiKey();

            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode root = objectMapper.readTree(response.body().string());
                if (!"ok".equals(root.path("s").asText())) return Collections.emptyList();

                JsonNode closes = root.path("c");
                JsonNode opens = root.path("o");
                JsonNode highs = root.path("h");
                JsonNode lows = root.path("l");
                JsonNode volumes = root.path("v");
                JsonNode timestamps = root.path("t");

                List<StockDailyData> dataList = new ArrayList<>();
                for (int i = 0; i < timestamps.size(); i++) {
                    StockDailyData data = new StockDailyData();
                    data.setStockCode(stockCode);
                    data.setTradeDate(LocalDate.ofEpochDay(timestamps.get(i).asLong() / 86400));
                    data.setOpenPrice(opens.get(i).asDouble());
                    data.setHighPrice(highs.get(i).asDouble());
                    data.setLowPrice(lows.get(i).asDouble());
                    data.setClosePrice(closes.get(i).asDouble());
                    data.setVolume(volumes.get(i).asLong());
                    data.setDataSource("finnhub");
                    if (i > 0) {
                        double prev = closes.get(i - 1).asDouble();
                        if (prev > 0) data.setChangePct((closes.get(i).asDouble() - prev) / prev * 100);
                    }
                    dataList.add(data);
                }
                return dataList;
            }
        } catch (Exception e) {
            log.error("Finnhub获取数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String url = BASE_URL + "/quote?symbol=" + stockCode.toUpperCase() + "&token=" + config.getFinnhubApiKey();
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode root = objectMapper.readTree(response.body().string());
                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("current_price", root.path("c").asDouble());
                quote.put("open_price", root.path("o").asDouble());
                quote.put("high_price", root.path("h").asDouble());
                quote.put("low_price", root.path("l").asDouble());
                quote.put("previous_close", root.path("pc").asDouble());
                double pc = root.path("pc").asDouble();
                double c = root.path("c").asDouble();
                if (pc > 0) quote.put("change_pct", (c - pc) / pc * 100);
                return quote;
            }
        } catch (Exception e) {
            log.error("Finnhub实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        if (!isAvailable()) return Collections.emptyMap();
        try {
            String url = BASE_URL + "/stock/profile2?symbol=" + stockCode.toUpperCase() + "&token=" + config.getFinnhubApiKey();
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode root = objectMapper.readTree(response.body().string());
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("stock_code", stockCode);
                info.put("stock_name", root.path("name").asText(""));
                info.put("industry", root.path("finnhubIndustry").asText(""));
                info.put("market_cap", root.path("marketCapitalization").asDouble());
                info.put("country", root.path("country").asText(""));
                info.put("exchange", root.path("exchange").asText(""));
                return info;
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
