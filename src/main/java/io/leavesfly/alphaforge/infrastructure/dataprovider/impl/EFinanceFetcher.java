package io.leavesfly.alphaforge.infrastructure.dataprovider.impl;

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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    public EFinanceFetcher(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
        return getHistoryData(stockCode, startDate, endDate, KLineFrequency.DAILY, AdjustType.FRONT);
    }

    /**
     * 多频率K线获取 — 支持日/周/月/分钟级 + 复权类型
     * klt映射: 101=日K, 102=周K, 103=月K, 1/5/15/30/60=分钟K
     * fqt映射: 0=不复权, 1=前复权, 2=后复权
     */
    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate,
                                                  KLineFrequency frequency, AdjustType adjust) {
        try {
            String secId = toSecId(stockCode);
            int klt = frequencyToKlt(frequency);
            int fqt = adjustToFqt(adjust);
            String url = KLINE_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=" + klt +
                    "&fqt=" + fqt +
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

    /** KLineFrequency → 东财 klt 参数 */
    private int frequencyToKlt(KLineFrequency freq) {
        return switch (freq) {
            case MINUTE_1 -> 1;
            case MINUTE_5 -> 5;
            case MINUTE_15 -> 15;
            case MINUTE_30 -> 30;
            case MINUTE_60 -> 60;
            case DAILY -> 101;
            case WEEKLY -> 102;
            case MONTHLY -> 103;
        };
    }

    /** AdjustType → 东财 fqt 参数 */
    private int adjustToFqt(AdjustType adjust) {
        return switch (adjust) {
            case NONE -> 0;
            case FRONT -> 1;
            case BACK -> 2;
        };
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        try {
            String secId = toSecId(stockCode);
            String url = QUOTE_URL + "?secid=" + secId +
                    "&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f60," +
                    "f116,f117,f162,f167,f170,f171," +
                    "f164,f163,f168,f169";

            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyMap();
                JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                if (data.isMissingNode()) return Collections.emptyMap();

                int dec = data.path("f59").asInt(3);
                double divisor = Math.pow(10, dec);

                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("stock_code", stockCode);
                quote.put("stock_name", data.path("f58").asText(""));
                quote.put("current_price", data.path("f43").asDouble() / divisor);
                quote.put("open_price", data.path("f46").asDouble() / divisor);
                quote.put("high_price", data.path("f44").asDouble() / divisor);
                quote.put("low_price", data.path("f45").asDouble() / divisor);
                quote.put("previous_close", data.path("f60").asDouble() / divisor);
                quote.put("volume", data.path("f47").asLong());
                quote.put("amount", data.path("f48").asDouble());
                quote.put("change_pct", data.path("f170").asDouble() / 100.0);
                quote.put("change_amount", data.path("f171").asDouble() / divisor);
                quote.put("turnover_rate", data.path("f167").asDouble() / 100.0);
                quote.put("pe", data.path("f162").asDouble() / 100.0);
                quote.put("market_cap", data.path("f116").asDouble());
                quote.put("circulating_cap", data.path("f117").asDouble());
                // 扩展字段
                quote.put("amplitude", data.path("f168").asDouble() / 100.0);  // 振幅%
                quote.put("volume_ratio", data.path("f50").asDouble() / 100.0);  // 量比
                quote.put("pe_static", data.path("f163").asDouble() / 100.0);  // 静态PE
                quote.put("limit_up", data.path("f51").asDouble() / divisor);  // 涨停价
                quote.put("limit_down", data.path("f52").asDouble() / divisor); // 跌停价
                quote.put("float_market_cap", data.path("f117").asDouble());  // 流通市值(亿)
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
    private static final String FUND_FLOW_DAILY_URL = "https://push2his.eastmoney.com/api/qt/stock/fflow/daykline/get";
    /** 东财 push2his 分钟级资金流接口 */
    private static final String FUND_FLOW_MINUTE_URL = "https://push2his.eastmoney.com/api/qt/stock/fflow/minutekline/get";

    /**
     * 获取日级资金流数据 — 东财 push2his
     * 返回主力/大单/中单/小单/超大单净流入
     */
    @Override
    public List<Map<String, Object>> getFundFlow(String stockCode, int days) {
        return getFundFlow(stockCode, days, false);
    }

    /**
     * 获取资金流数据 — 支持日级/分钟级
     * @param stockCode 股票代码
     * @param days      返回最近天数（日级）或数据条数（分钟级）
     * @param minuteLevel true=分钟级，false=日级
     */
    public List<Map<String, Object>> getFundFlow(String stockCode, int days, boolean minuteLevel) {
        try {
            String secId = toSecId(stockCode);
            String baseUrl = minuteLevel ? FUND_FLOW_MINUTE_URL : FUND_FLOW_DAILY_URL;
            int klt = minuteLevel ? 1 : 101;  // 101=日K, 1=1分钟
            String url = baseUrl + "?secid=" + secId +
                    "&klt=" + klt + "&lmt=" + days +
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
                    // 扩展字段
                    row.put("bps", item.path("BPS").asDouble(0));  // 每股净资产
                    row.put("roic", item.path("ROIC").asDouble(0));  // 投入资本回报率
                    row.put("equity_ratio", item.path("EQUITY_RATIO").asDouble(0));  // 产权比率
                    row.put("dps", item.path("DPS").asDouble(0));  // 每股股息
                    row.put("dividend_ratio", item.path("DIVI_RATIO").asDouble(0));  // 股息率%
                    row.put("per_netcash_operate", item.path("PER_NETCASH_OPERATE").asDouble(0));  // 每股经营现金流
                    row.put("ocf_sales", item.path("OCF_SALES").asDouble(0));  // 经营现金流/营收%
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取关键指标失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 信号层数据 ==========

    /**
     * 获取龙虎榜数据 — 东财 datacenter
     * 上榜记录 + 买卖席位 TOP5 + 机构动向
     */
    @Override
    public List<Map<String, Object>> getDragonTigerList(String stockCode, int days) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_DAILYBILLBOARD_DETAILS"
                    + "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=" + days
                    + "&sortColumns=TRADE_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("trade_date", item.path("TRADE_DATE").asText(""));
                    row.put("reason", item.path("EXPLAIN").asText(""));          // 上榜原因
                    row.put("net_buy", item.path("NET").asDouble(0));              // 净买入额
                    row.put("buy_amount", item.path("BUY").asDouble(0));           // 买入额
                    row.put("sell_amount", item.path("SELL").asDouble(0));         // 卖出额
                    row.put("close_price", item.path("CLOSE_PRICE").asDouble(0));
                    row.put("change_pct", item.path("CHANGE_RATE").asDouble(0));    // 涨跌幅%
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取龙虎榜失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取北向资金流向 — 东财 datacenter
     * 沪股通/深股通日级净买入数据
     */
    @Override
    public List<Map<String, Object>> getNorthboundFlow(int days) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_MUTUAL_DEAL_HISTORY"
                    + "&columns=ALL"
                    + "&pageNumber=1&pageSize=" + days
                    + "&sortColumns=TRADE_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("trade_date", item.path("TRADE_DATE").asText(""));
                    row.put("board_type", item.path("BOARD_TYPE").asText(""));   // 沪股通/深股通
                    row.put("buy_amount", item.path("BUY_AMOUNT").asDouble(0));   // 买入额
                    row.put("sell_amount", item.path("SELL_AMOUNT").asDouble(0)); // 卖出额
                    row.put("net_amount", item.path("NET_AMOUNT").asDouble(0));   // 净买入额
                    row.put("accumulate_amount", item.path("ACCUMULATE_AMOUNT").asDouble(0)); // 累计净买入
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取北向资金失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取个股板块归属详情 — 东财 slist
     * 行业/概念/地域 + BK码 + 当日涨跌幅 + 龙头股
     */
    @Override
    public List<Map<String, Object>> getStockBoardsDetail(String stockCode) {
        try {
            // 东财数据中心：个股所属板块
            String url = DATACENTER_URL + "?reportName=RPT_F10_CORETHEME_BOARDTYPE"
                    + "&columns=BOARD_NAME,BOARD_CODE,BOARD_TYPE"
                    + "&filter=(SECURITY_CODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=50"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("board_name", item.path("BOARD_NAME").asText(""));
                    row.put("board_code", item.path("BOARD_CODE").asText(""));
                    row.put("board_type", item.path("BOARD_TYPE").asText("")); // 行业/概念/地域
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取板块归属失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 杠杆与筹码数据 ==========

    /**
     * 获取融资融券明细 — 东财 datacenter
     * 日级融资余额/买入/偿还 + 融券余额/卖出/偿还
     */
    @Override
    public List<Map<String, Object>> getMarginTrading(String stockCode, int days) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_RZRQ_DETAIL"
                    + "&columns=ALL&filter=(SCODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=" + days
                    + "&sortColumns=DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", item.path("DATE").asText(""));
                    row.put("rzye", item.path("RZYE").asDouble(0));    // 融资余额
                    row.put("rzmre", item.path("RZMRE").asDouble(0));  // 融资买入额
                    row.put("rzche", item.path("RZCHE").asDouble(0));  // 融资偿还额
                    row.put("rqye", item.path("RQYE").asDouble(0));    // 融券余额
                    row.put("rqmcl", item.path("RQMCL").asDouble(0));  // 融券卖出量
                    row.put("rzrqye", item.path("RZRQYE").asDouble(0)); // 融资融券余额
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取融资融券失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取股东户数变化 — 东财 datacenter
     * 季度股东数 + 环比变化 + 户均持股（筹码集中度）
     */
    @Override
    public List<Map<String, Object>> getShareholderCount(String stockCode) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_F10_EH_HOLDERNUM"
                    + "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=8"
                    + "&sortColumns=END_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("end_date", item.path("END_DATE").asText(""));
                    row.put("holder_num", item.path("HOLDER_NUM").asInt(0));         // 股东户数
                    row.put("holder_num_change", item.path("HOLDER_NUM_CHANGE").asDouble(0)); // 环比变化%
                    row.put("avg_hold_amount", item.path("AVG_HOLD_AMOUNT").asDouble(0)); // 户均持股
                    row.put("avg_hold_ratio", item.path("AVG_HOLD_RATIO").asDouble(0));   // 户均持股比例%
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取股东户数失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取分红送转历史 — 东财 datacenter
     * 每股派息/送股/转增 + 进度状态
     */
    @Override
    public List<Map<String, Object>> getDividendHistory(String stockCode) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_F10_EH_DIVIDENT"
                    + "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=20"
                    + "&sortColumns=REPORT_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("report_date", item.path("REPORT_DATE").asText(""));
                    row.put("dps", item.path("DPS").asDouble(0));               // 每股派息(税前)
                    row.put("send_stock", item.path("SEND_STOCK").asDouble(0));  // 每股送股
                    row.put("convert_stock", item.path("CONVERT_STOCK").asDouble(0)); // 每股转增
                    row.put("progress", item.path("PROGRESS").asText(""));      // 进度状态
                    row.put("ex_date", item.path("EX_DIVIDEND_DATE").asText("")); // 除权除息日
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取分红送转失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 研报与公告数据 ==========

    /** 东财研报 API */
    private static final String REPORT_URL = "https://reportapi.eastmoney.com/report/list";
    /** 东财公告 API */
    private static final String ANNOUNCE_URL = "https://np-anotice.eastmoney.com/api/security/ann";

    /**
     * 获取个股研报列表 — 东财 reportapi
     * 包含评级 + 三年EPS预测
     */
    @Override
    public List<Map<String, Object>> getResearchReports(String stockCode, int count) {
        try {
            String url = REPORT_URL + "?industryCode=*&industry=*&rating=*&ratingChange=*&beginTime=&endTime=&pageNo=1&pageSize="
                    + count + "&code=" + stockCode;

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode dataNode = objectMapper.readTree(response.body().string()).path("data");
                if (!dataNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : dataNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", item.path("title").asText(""));
                    row.put("org_name", item.path("orgSName").asText(""));    // 研究机构
                    row.put("author", item.path("researcher").asText(""));      // 研究员
                    row.put("rating", item.path("emRatingName").asText(""));    // 评级
                    row.put("publish_date", item.path("publishDate").asText(""));
                    row.put("predict_eps_this_year", item.path("predictThisEps").asDouble(0)); // 今年EPS预测
                    row.put("predict_eps_next_year", item.path("predictNextEps").asDouble(0)); // 明年EPS预测
                    row.put("predict_pe_this_year", item.path("predictThisPe").asDouble(0));   // 今年PE预测
                    row.put("predict_pe_next_year", item.path("predictNextPe").asDouble(0));   // 明年PE预测
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取研报失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取机构一致预期EPS — 东财 datacenter 业绩预告
     * 预增/预减/续盈/扭亏 等业绩预告数据
     */
    @Override
    public List<Map<String, Object>> getConsensusEPS(String stockCode) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_F10_FINANCE_YJBG"
                    + "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")"
                    + "&pageNumber=1&pageSize=8"
                    + "&sortColumns=NOTICE_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("notice_date", item.path("NOTICE_DATE").asText(""));
                    row.put("report_date", item.path("REPORT_DATE").asText(""));
                    row.put("forecast_type", item.path("FORECAST_TYPE").asText(""));     // 预增/预减/续盈/扭亏
                    row.put("profit_forecast", item.path("PROFIT_FORECAST").asDouble(0));  // 预测净利润
                    row.put("profit_change_pct", item.path("CHANGE_PCT").asDouble(0));    // 净利润变动幅度%
                    row.put("eps_forecast", item.path("EPS_FORECAST").asDouble(0));      // 预测每股收益
                    row.put("summary", item.path("SUMMARY").asText(""));                 // 业绩变动原因
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取一致预期失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取个股公告列表 — 东财公告 API
     * 沪深北全量公告
     */
    @Override
    public List<Map<String, Object>> getAnnouncements(String stockCode, int count) {
        try {
            String url = ANNOUNCE_URL + "?srp=&page_size=" + count
                    + "&page_index=1&ann_type=A&client_source=web&stock_list=" + stockCode;

            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode listNode = objectMapper.readTree(response.body().string()).path("data").path("list");
                if (!listNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : listNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", item.path("title").asText(""));
                    row.put("notice_date", item.path("notice_date").asText(""));
                    row.put("ann_type", item.path("ann_type").asText(""));
                    row.put("art_code", item.path("art_code").asText(""));
                    // 构建公告详情URL
                    String artCode = item.path("art_code").asText("");
                    if (!artCode.isEmpty()) {
                        row.put("url", "https://np-cnotice.eastmoney.com/api/content/ann?art_code=" + artCode);
                    } else {
                        row.put("url", "");
                    }
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取公告失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== 事件驱动数据 ==========

    /** 东财大宗交易接口 */
    private static final String BLOCK_TRADE_REPORT = "RPT_BLOCK_TRADE_DETAIL";

    /**
     * 获取大宗交易数据 — 东财 datacenter
     * 返回成交价/量/买卖方营业部/折价率
     */
    @Override
    public List<Map<String, Object>> getBlockTrades(String stockCode, int days) {
        try {
            String url = DATACENTER_URL + "?reportName=" + BLOCK_TRADE_REPORT +
                    "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")" +
                    "&pageNumber=1&pageSize=" + days +
                    "&sortColumns=TRADE_DATE&sortTypes=-1" +
                    "&source=WEB&client=WEB";
            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("trade_date", item.path("TRADE_DATE").asText(""));
                    row.put("price", item.path("PRICE").asDouble(0));
                    row.put("volume", item.path("DEAL_NUM").asDouble(0));
                    row.put("buyer", item.path("BUYER_NAME").asText(""));
                    row.put("seller", item.path("SELLER_NAME").asText(""));
                    row.put("discount_rate", item.path("PREMIUM_RATIO").asDouble(0));  // 折溢价率%
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取大宗交易失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取限售解禁日历 — 东财 datacenter
     * 返回解禁日期/解禁股数/解禁比例/上市日期
     */
    @Override
    public List<Map<String, Object>> getRestrictedShareUnlock(String stockCode, int days) {
        try {
            String url = DATACENTER_URL + "?reportName=RPT_LIFTUP_LISTINFO" +
                    "&columns=ALL&filter=(SECURITY_CODE=\"" + stockCode + "\")" +
                    "&pageNumber=1&pageSize=" + days +
                    "&sortColumns=LIFTUP_DATE&sortTypes=-1" +
                    "&source=WEB&client=WEB";
            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("unlock_date", item.path("LIFTUP_DATE").asText(""));
                    row.put("share_count", item.path("LIFTUP_NUM").asDouble(0));
                    row.put("ratio", item.path("LIFTUP_RATIO").asDouble(0));  // 占总股本比例%
                    row.put("list_date", item.path("LISTING_DATE").asText(""));
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取解禁日历失败: {} - {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取行业板块排名 — 东财 push2 clist
     * 返回行业名称/涨跌幅/领涨股/上涨下跌家数
     */
    @Override
    public List<Map<String, Object>> getIndustryRanking() {
        try {
            String url = "https://push2.eastmoney.com/api/qt/clist/get?fs=m:90+t:2" +
                    "&fields=f2,f3,f4,f12,f14,f128,f136,f140" +
                    "&pn=1&pz=50&fid=f3&po=1";
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode dataNode = objectMapper.readTree(response.body().string()).path("data");
                JsonNode diff = dataNode.path("diff");
                if (!diff.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : diff) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("board_code", item.path("f12").asText(""));
                    row.put("board_name", item.path("f14").asText(""));
                    row.put("change_pct", item.path("f3").asDouble(0) / 100.0);  // 涨跌幅%
                    row.put("lead_stock", item.path("f140").asText(""));  // 领涨股
                    row.put("rise_count", item.path("f136").asInt(0));  // 上涨家数
                    row.put("fall_count", item.path("f128").asInt(0));  // 下跌家数
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取行业排名失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取全市场龙虎榜 — 东财 datacenter
     * 返回每日全市场上榜股票 + 净买额排名
     */
    @Override
    public List<Map<String, Object>> getMarketDragonTiger(LocalDate date) {
        try {
            String dateStr = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            String url = DATACENTER_URL + "?reportName=RPT_DAILYBILLBOARD_DETAILS" +
                    "&columns=ALL&filter=(TRADE_DATE='" + dateStr + "')" +
                    "&pageNumber=1&pageSize=50" +
                    "&sortColumns=NET&sortTypes=-1" +
                    "&source=WEB&client=WEB";
            Request request = new Request.Builder().url(url).header("User-Agent", UA).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                JsonNode resultNode = objectMapper.readTree(response.body().string()).path("result").path("data");
                if (!resultNode.isArray()) return Collections.emptyList();

                List<Map<String, Object>> result = new ArrayList<>();
                for (JsonNode item : resultNode) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("trade_date", item.path("TRADE_DATE").asText(""));
                    row.put("stock_code", item.path("SECURITY_CODE").asText(""));
                    row.put("stock_name", item.path("SECURITY_NAME_ABBR").asText(""));
                    row.put("net_buy", item.path("NET").asDouble(0));
                    row.put("buy_amount", item.path("BUY").asDouble(0));
                    row.put("sell_amount", item.path("SELL").asDouble(0));
                    row.put("reason", item.path("EXPLAIN").asText(""));
                    row.put("change_pct", item.path("CHANGE_RATE").asDouble(0));
                    result.add(row);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("EFinance获取全市场龙虎榜失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 转换为东方财富secid格式
     * 沪市: 1.代码, 深市: 0.代码, 北交所: 0.代码
     */
    private String toSecId(String stockCode) {
        return StockCodeUtils.toSecId(stockCode);
    }
}
