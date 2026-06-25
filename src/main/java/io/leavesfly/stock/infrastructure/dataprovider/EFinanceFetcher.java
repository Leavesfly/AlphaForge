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
 * EFinance数据源适配器(Java版)
 *
 * 原理: efinance库本质是调用东方财富pushapi接口，Java可直接调用相同HTTP端点
 * 接口:
 *   - 历史K线: push2his.eastmoney.com
 *   - 实时行情: push2.eastmoney.com  
 *   - 板块数据: push2.eastmoney.com/api/qt/clist/get
 */
@Component
public class EFinanceFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(EFinanceFetcher.class);
    
    /** 东方财富历史K线接口 */
    private static final String KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    /** 东方财富实时行情接口 */
    private static final String QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    /** 板块列表接口 */
    private static final String BOARD_URL = "https://push2.eastmoney.com/api/qt/clist/get";
    /** 个股所属板块接口 */
    private static final String BELONG_URL = "https://datacenter.eastmoney.com/securities/api/data/v1/get";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EFinanceFetcher(AppConfig config) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    @Override public String getName() { return "efinance"; }
    @Override public int getPriority() { return 0; } // 最高优先级
    @Override public boolean isAvailable() { return true; } // 无需API Key

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String secId = toSecId(stockCode);
            String url = KLINE_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=101" +  // 日K
                    "&fqt=1" +    // 前复权
                    "&beg=" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "&end=" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "&lmt=1000" +
                    "&_=" + System.currentTimeMillis();

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode klines = root.path("data").path("klines");
                String stockName = root.path("data").path("name").asText("");

                if (!klines.isArray()) return Collections.emptyList();

                List<StockDailyData> result = new ArrayList<>();
                for (JsonNode line : klines) {
                    String[] parts = line.asText().split(",");
                    if (parts.length < 11) continue;
                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setStockName(stockName);
                    d.setTradeDate(LocalDate.parse(parts[0]));
                    d.setOpenPrice(Double.parseDouble(parts[1]));
                    d.setClosePrice(Double.parseDouble(parts[2]));
                    d.setHighPrice(Double.parseDouble(parts[3]));
                    d.setLowPrice(Double.parseDouble(parts[4]));
                    d.setVolume(Long.parseLong(parts[5]));
                    d.setAmount(Double.parseDouble(parts[6]));
                    d.setAmplitude(Double.parseDouble(parts[7]));
                    d.setChangePct(Double.parseDouble(parts[8]));
                    d.setChangeAmount(Double.parseDouble(parts[9]));
                    d.setTurnoverRate(Double.parseDouble(parts[10]));
                    d.setDataSource("efinance");
                    result.add(d);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取历史数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String secId = toSecId(stockCode);
            String url = QUOTE_URL + "?secid=" + secId +
                    "&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f60,f116,f117,f162,f167,f170,f171";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                if (data.isMissingNode()) return Collections.emptyMap();

                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("stock_name", data.path("f58").asText(""));
                quote.put("current_price", data.path("f43").asDouble() / 100.0);
                quote.put("open_price", data.path("f46").asDouble() / 100.0);
                quote.put("high_price", data.path("f44").asDouble() / 100.0);
                quote.put("low_price", data.path("f45").asDouble() / 100.0);
                quote.put("previous_close", data.path("f60").asDouble() / 100.0);
                quote.put("volume", data.path("f47").asLong());
                quote.put("amount", data.path("f48").asDouble());
                quote.put("change_pct", data.path("f170").asDouble() / 100.0);
                quote.put("change_amount", data.path("f171").asDouble() / 100.0);
                quote.put("turnover_rate", data.path("f167").asDouble() / 100.0);
                quote.put("pe", data.path("f162").asDouble() / 100.0);
                quote.put("market_cap", data.path("f116").asDouble());
                quote.put("circulating_cap", data.path("f117").asDouble());
                return quote;
            }
        } catch (Exception e) {
            log.error("EFinance实时行情失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) { return getRealtimeQuote(stockCode); }

    @Override
    public List<String> getStockBoards(String stockCode) {
        // 获取个股所属板块(通过东方财富数据中心接口)
        try {
            String url = BELONG_URL + "?reportName=RPT_F10_CORETHEME_BOARDTYPE" +
                    "&columns=BOARD_NAME,BOARD_CODE,BOARD_TYPE" +
                    "&filter=(SECURITY_CODE=%22" + stockCode + "%22)";
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return List.of();
                JsonNode result = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!result.isArray()) return List.of();
                List<String> boards = new ArrayList<>();
                for (JsonNode item : result) {
                    boards.add(item.path("BOARD_NAME").asText());
                }
                return boards;
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 转换为东方财富secid格式
     * 沪市: 1.代码, 深市: 0.代码, 北交所: 0.代码
     */
    private String toSecId(String stockCode) {
        String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
        if (code.startsWith("6") || code.startsWith("9") || code.startsWith("11") || code.startsWith("13")) {
            return "1." + code;
        }
        return "0." + code;
    }
}
