package io.leavesfly.stock.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.model.entity.StockDailyData;
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
 * AKShare数据源适配器(通过HTTP API调用)
 * 
 * 对应Python版本的akshare_fetcher.py
 * 由于Java没有akshare库，通过部署的akshare HTTP服务获取数据
 * 或者直接调用东方财富等公开HTTP接口
 */
@Component
public class AkShareFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(AkShareFetcher.class);
    private static final String EASTMONEY_HISTORY_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String EASTMONEY_REALTIME_URL = "https://push2.eastmoney.com/api/qt/stock/get";

    private final AppConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AkShareFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "akshare";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        return true; // 东方财富接口始终可用
    }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String secId = convertToSecId(stockCode);
            String url = EASTMONEY_HISTORY_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=101" + // 日K
                    "&fqt=1" +   // 前复权
                    "&beg=" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "&end=" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE);

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyList();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode klines = root.path("data").path("klines");

                if (klines.isMissingNode() || !klines.isArray()) {
                    return Collections.emptyList();
                }

                List<StockDailyData> result = new ArrayList<>();
                String stockName = root.path("data").path("name").asText("");

                for (JsonNode kline : klines) {
                    String[] parts = kline.asText().split(",");
                    if (parts.length < 11) continue;

                    StockDailyData data = new StockDailyData();
                    data.setStockCode(stockCode);
                    data.setStockName(stockName);
                    data.setTradeDate(LocalDate.parse(parts[0]));
                    data.setOpenPrice(parseDouble(parts[1]));
                    data.setClosePrice(parseDouble(parts[2]));
                    data.setHighPrice(parseDouble(parts[3]));
                    data.setLowPrice(parseDouble(parts[4]));
                    data.setVolume(parseLong(parts[5]));
                    data.setAmount(parseDouble(parts[6]));
                    data.setAmplitude(parseDouble(parts[7]));
                    data.setChangePct(parseDouble(parts[8]));
                    data.setChangeAmount(parseDouble(parts[9]));
                    data.setTurnoverRate(parseDouble(parts[10]));
                    data.setDataSource("akshare");
                    result.add(data);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("AKShare获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String secId = convertToSecId(stockCode);
            String url = EASTMONEY_REALTIME_URL + "?" +
                    "secid=" + secId +
                    "&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f60,f116,f117,f162,f167,f170";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode data = root.path("data");

                if (data.isMissingNode()) return Collections.emptyMap();

                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("stock_name", data.path("f58").asText(""));
                quote.put("current_price", data.path("f43").asDouble() / 100.0);
                quote.put("open_price", data.path("f46").asDouble() / 100.0);
                quote.put("high_price", data.path("f44").asDouble() / 100.0);
                quote.put("low_price", data.path("f45").asDouble() / 100.0);
                quote.put("volume", data.path("f47").asLong());
                quote.put("amount", data.path("f48").asDouble());
                quote.put("change_pct", data.path("f170").asDouble() / 100.0);
                quote.put("turnover_rate", data.path("f167").asDouble() / 100.0);
                quote.put("pe", data.path("f162").asDouble() / 100.0);
                quote.put("market_cap", data.path("f116").asDouble());
                return quote;
            }
        } catch (Exception e) {
            log.error("AKShare获取实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) {
        // 通过实时行情获取基本信息
        return getRealtimeQuote(stockCode);
    }

    /**
     * 将股票代码转换为东方财富secid格式
     * 沪市: 1.代码, 深市: 0.代码
     */
    private String convertToSecId(String stockCode) {
        // 去除前缀
        String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
        if (code.startsWith("6") || code.startsWith("9")) {
            return "1." + code; // 沪市
        } else if (code.startsWith("0") || code.startsWith("3") || code.startsWith("2")) {
            return "0." + code; // 深市
        } else if (code.startsWith("4") || code.startsWith("8")) {
            return "0." + code; // 北交所
        }
        return "1." + code;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
