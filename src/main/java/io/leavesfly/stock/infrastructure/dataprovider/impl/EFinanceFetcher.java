package io.leavesfly.stock.infrastructure.dataprovider.impl;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.stock.infrastructure.dataprovider.BaseDataFetcher;
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

    /** 东财系接口有风控会封IP，限流间隔 1000ms */
    @Override public long getRateLimitMs() { return 1000; }

    /** 东财 push2his kline 仅支持A股K线，不支持美股/港股K线 */
    @Override public Set<MarketType> getSupportedMarkets() { return Set.of(MarketType.A); }

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

    /**
     * 获取分钟级K线数据
     * 通过东方财富分时接口获取（与AkShare同源，优先级最高）
     *
     * @param stockCode 股票代码
     * @param period    周期(1/5/15/30/60分钟)
     * @param count     数据条数
     * @return K线数据列表
     */
    @Override
    public List<Map<String, Object>> getMinuteData(String stockCode, int period, int count) {
        try {
            String secId = toSecId(stockCode);
            int klt = switch (period) {
                case 1 -> 1;
                case 5 -> 5;
                case 15 -> 15;
                case 30 -> 30;
                case 60 -> 60;
                default -> 5;
            };
            String url = KLINE_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=" + klt +
                    "&fqt=1" +
                    "&lmt=" + count +
                    "&end=20500101" +
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
                if (!klines.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode line : klines) {
                    String[] parts = line.asText().split(",");
                    if (parts.length < 7) continue;
                    Map<String, Object> bar = new LinkedHashMap<>();
                    bar.put("time", parts[0]);
                    bar.put("open", Double.parseDouble(parts[1]));
                    bar.put("close", Double.parseDouble(parts[2]));
                    bar.put("high", Double.parseDouble(parts[3]));
                    bar.put("low", Double.parseDouble(parts[4]));
                    bar.put("volume", Long.parseLong(parts[5]));
                    bar.put("amount", Double.parseDouble(parts[6]));
                    result.add(bar);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取分钟数据失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

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

    // ========== 资金面数据 ==========

    /** 东财 push2his 日级资金流接口 */
    private static final String FUND_FLOW_URL = "https://push2his.eastmoney.com/api/qt/stock/fflow/daykline/get";

    /**
     * 获取日级资金流数据 — 东财 push2his
     * 返回主力/大单/中单/小单/超大单净流入
     */
    @Override
    public List<Map<String, Object>> getFundFlow(String stockCode, int days) {
        try {
            String secId = toSecId(stockCode);
            String url = FUND_FLOW_URL + "?secid=" + secId +
                    "&klt=101&lmt=" + days +
                    "&fields1=f1,f2,f3,f7" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                JsonNode klines = data.path("klines");
                if (!klines.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode line : klines) {
                    String[] parts = line.asText().split(",");
                    if (parts.length < 6) continue;
                    Map<String, Object> flow = new LinkedHashMap<>();
                    flow.put("date", parts[0]);
                    flow.put("main_net", Double.parseDouble(parts[1]));       // 主力净流入
                    flow.put("small_net", Double.parseDouble(parts[2]));     // 小单净流入
                    flow.put("mid_net", Double.parseDouble(parts[3]));       // 中单净流入
                    flow.put("big_net", Double.parseDouble(parts[4]));       // 大单净流入
                    flow.put("super_big_net", Double.parseDouble(parts[5])); // 超大单净流入
                    if (parts.length > 6 && !parts[6].isEmpty()) {
                        flow.put("main_pct", Double.parseDouble(parts[6])); // 主力净占比%
                    }
                    result.add(flow);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取资金流失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 基本面数据 ==========

    /** 东财数据中心统一接口 */
    private static final String DATACENTER_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    /**
     * 获取财报三表 — 东财 datacenter
     * statementType: "balance" / "income" / "cashflow"
     */
    @Override
    public List<Map<String, Object>> getFinancialStatements(String stockCode, String statementType) {
        try {
            // 报表名映射
            Map<String, String> reportMap = Map.of(
                    "balance", "RPT_F10_FINANCE_GINCOMESTATEMENT",
                    "income", "RPT_F10_FINANCE_GINCOMESTATEMENT",
                    "cashflow", "RPT_F10_FINANCE_GCASHFLOWSTATEMENT"
            );
            String reportName = reportMap.getOrDefault(statementType, "RPT_F10_FINANCE_GINCOMESTATEMENT");

            String url = DATACENTER_URL + "?reportName=" + reportName +
                    "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")" +
                    "&pageNumber=1&pageSize=20&sortColumns=REPORT_DATE&sortTypes=-1" +
                    "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", UA).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string())
                        .path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("report_date", item.path("REPORT_DATE").asText(""));
                    row.put("item_name", item.path("ITEM_NAME").asText(""));
                    row.put("amount", item.path("AMOUNT").asDouble(0));
                    row.put("yoy_ratio", item.path("YOY_RATIO").asDouble(0));
                    row.put("report", item.path("REPORT").asText(""));
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取财报失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取关键财务指标 — 东财 GMAININDICATOR
     * 包含 ROE/ROA/EPS/毛利率/资产负债率等
     */
    @Override
    public List<Map<String, Object>> getKeyIndicators(String stockCode) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_F10_FINANCE_GMAININDICATOR" +
                    "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")" +
                    "&pageNumber=1&pageSize=4&sortColumns=REPORT_DATE&sortTypes=-1" +
                    "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", UA).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string())
                        .path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("report_date", item.path("REPORT_DATE").asText(""));
                    row.put("operate_income", item.path("OPERATE_INCOME").asDouble(0));
                    row.put("basic_eps", item.path("BASIC_EPS").asDouble(0));
                    row.put("roe_avg", item.path("ROE_AVG").asDouble(0));
                    row.put("roa", item.path("ROA").asDouble(0));
                    row.put("gross_profit_ratio", item.path("GROSS_PROFIT_RATIO").asDouble(0));
                    row.put("net_profit_ratio", item.path("NET_PROFIT_RATIO").asDouble(0));
                    row.put("debt_asset_ratio", item.path("DEBT_ASSET_RATIO").asDouble(0));
                    row.put("current_ratio", item.path("CURRENT_RATIO").asDouble(0));
                    row.put("operate_income_yoy", item.path("OPERATE_INCOME_YOY").asDouble(0));
                    row.put("basic_eps_yoy", item.path("BASIC_EPS_YOY").asDouble(0));
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取关键指标失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
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
