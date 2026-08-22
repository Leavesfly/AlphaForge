package io.leavesfly.alphaforge.infrastructure.dataprovider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.AdjustType;
import io.leavesfly.alphaforge.domain.model.enums.KLineFrequency;
import io.leavesfly.alphaforge.util.StockCodeUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 东方财富 push2 系公开接口的统一访问客户端。
 *
 * <p>efinance / akshare 两个数据源本质上都是调用东方财富相同的 HTTP 端点
 * （{@code push2his.eastmoney.com} 历史K线、{@code push2.eastmoney.com} 实时行情）。
 * 过去两个 Fetcher 各自复制了一份 URL 拼装、klt/fqt 映射、JSON 解析逻辑，
 * 导致维护成本翻倍、行情字段口径分叉。</p>
 *
 * <p>本类将这些公共逻辑收敛为单一实现，Fetcher 只需委托即可，
 * 东财特有的资金流/财报/龙虎榜等能力仍留在各自 Fetcher 中扩展。</p>
 */
@Component
public class EastmoneyDataClient {

    private static final Logger log = LoggerFactory.getLogger(EastmoneyDataClient.class);

    /** 历史/分钟 K线接口 */
    private static final String KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    /** 实时行情接口 */
    private static final String QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final String REFERER = "https://quote.eastmoney.com/";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EastmoneyDataClient(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取多频率历史 K 线。
     *
     * @param stockCode  股票代码
     * @param startDate  起始日期
     * @param endDate    结束日期
     * @param frequency  K 线频率（日/周/月/分钟）
     * @param adjust     复权类型
     * @param sourceTag  写入 {@link StockDailyData#setDataSource(String)} 的来源标签
     */
    public List<StockDailyData> fetchHistory(String stockCode, LocalDate startDate, LocalDate endDate,
                                             KLineFrequency frequency, AdjustType adjust, String sourceTag) {
        try {
            String secId = StockCodeUtils.toSecId(stockCode);
            String url = KLINE_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=" + frequencyToKlt(frequency) +
                    "&fqt=" + adjustToFqt(adjust) +
                    "&beg=" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "&end=" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE) +
                    "&lmt=1000" +
                    "&_=" + System.currentTimeMillis();

            JsonNode root = get(url);
            if (root == null) return Collections.emptyList();
            JsonNode klines = root.path("data").path("klines");
            if (!klines.isArray()) return Collections.emptyList();

            String stockName = root.path("data").path("name").asText("");
            List<StockDailyData> result = new ArrayList<>();
            for (JsonNode line : klines) {
                String[] parts = line.asText().split(",");
                if (parts.length < 11) continue;
                StockDailyData d = new StockDailyData();
                d.setStockCode(stockCode);
                d.setStockName(stockName);
                d.setTradeDate(LocalDate.parse(parts[0]));
                d.setOpenPrice(parseDouble(parts[1]));
                d.setClosePrice(parseDouble(parts[2]));
                d.setHighPrice(parseDouble(parts[3]));
                d.setLowPrice(parseDouble(parts[4]));
                d.setVolume(parseLong(parts[5]));
                d.setAmount(parseDouble(parts[6]));
                d.setAmplitude(parseDouble(parts[7]));
                d.setChangePct(parseDouble(parts[8]));
                d.setChangeAmount(parseDouble(parts[9]));
                d.setTurnoverRate(parseDouble(parts[10]));
                d.setDataSource(sourceTag);
                result.add(d);
            }
            return result;
        } catch (DataSourceUnavailableException e) {
            throw e; // 传输层故障上抛，由故障切换器计入失败并熔断
        } catch (Exception e) {
            log.error("东财历史K线获取失败[{}]: {} - {}", sourceTag, stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取实时行情（含涨跌停、量比、PE、市值等扩展字段）。
     */
    public Map<String, Object> fetchRealtimeQuote(String stockCode, String sourceTag) {
        try {
            String secId = StockCodeUtils.toSecId(stockCode);
            String url = QUOTE_URL + "?secid=" + secId +
                    "&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f59,f60," +
                    "f116,f117,f162,f167,f170,f171,f164,f163,f168,f169";

            JsonNode root = get(url);
            if (root == null) return Collections.emptyMap();
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return Collections.emptyMap();

            int dec = data.path("f59").asInt(2);
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
            quote.put("amplitude", data.path("f168").asDouble() / 100.0);
            quote.put("volume_ratio", data.path("f50").asDouble() / 100.0);
            quote.put("pe_static", data.path("f163").asDouble() / 100.0);
            quote.put("limit_up", data.path("f51").asDouble() / divisor);
            quote.put("limit_down", data.path("f52").asDouble() / divisor);
            quote.put("float_market_cap", data.path("f117").asDouble());
            return quote;
        } catch (DataSourceUnavailableException e) {
            throw e; // 传输层故障上抛，由故障切换器计入失败并熔断
        } catch (Exception e) {
            log.error("东财实时行情失败[{}]: {}", sourceTag, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 获取分钟级 K 线（返回通用 Map 结构）。
     *
     * @param period 周期分钟数(1/5/15/30/60)
     * @param count  数据条数
     */
    public List<Map<String, Object>> fetchMinuteData(String stockCode, int period, int count, String sourceTag) {
        try {
            String secId = StockCodeUtils.toSecId(stockCode);
            int klt = minutePeriodToKlt(period);
            String url = KLINE_URL + "?" +
                    "secid=" + secId +
                    "&fields1=f1,f2,f3,f4,f5,f6" +
                    "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
                    "&klt=" + klt +
                    "&fqt=1" +
                    "&lmt=" + count +
                    "&end=20500101" +
                    "&_=" + System.currentTimeMillis();

            JsonNode root = get(url);
            if (root == null) return Collections.emptyList();
            JsonNode klines = root.path("data").path("klines");
            if (!klines.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode line : klines) {
                String[] parts = line.asText().split(",");
                if (parts.length < 7) continue;
                Map<String, Object> bar = new LinkedHashMap<>();
                bar.put("time", parts[0]);
                bar.put("open", parseDouble(parts[1]));
                bar.put("close", parseDouble(parts[2]));
                bar.put("high", parseDouble(parts[3]));
                bar.put("low", parseDouble(parts[4]));
                bar.put("volume", parseLong(parts[5]));
                bar.put("amount", parseDouble(parts[6]));
                result.add(bar);
            }
            return result;
        } catch (DataSourceUnavailableException e) {
            throw e; // 传输层故障上抛，由故障切换器计入失败并熔断
        } catch (Exception e) {
            log.error("东财分钟K线获取失败[{}]: {} - {}", sourceTag, stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 发起 GET 并解析 JSON。
     *
     * @return 解析后的 JSON；非 2xx（非 5xx/429）或空响应体时返回 {@code null} 表示无数据
     * @throws DataSourceUnavailableException 传输层故障或源侧 5xx/429，需触发熔断与换源
     */
    private JsonNode get(String url) {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // 5xx 与 429 属源侧故障/限流，需触发熔断；其余非 2xx 视为无数据
                int code = response.code();
                if (code >= 500 || code == 429) {
                    throw new DataSourceUnavailableException(
                            "东财接口 HTTP " + code + ": " + hostOf(url));
                }
                return null;
            }
            if (response.body() == null) return null;
            return objectMapper.readTree(response.body().string());
        } catch (java.io.IOException e) {
            // 连接重置/超时/DNS 失败等传输层故障：上抛以便熔断，不得隐形降级为空结果
            throw new DataSourceUnavailableException(
                    "东财接口不可达 " + hostOf(url) + ": " + e.getMessage(), e);
        }
    }

    /** 仅取主机名用于日志/异常信息，避免把带参长 URL 写进日志 */
    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception ignored) {
            return url;
        }
    }

    /** KLineFrequency → 东财 klt 参数 */
    public static int frequencyToKlt(KLineFrequency freq) {
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
    public static int adjustToFqt(AdjustType adjust) {
        return switch (adjust) {
            case NONE -> 0;
            case FRONT -> 1;
            case BACK -> 2;
        };
    }

    private static int minutePeriodToKlt(int period) {
        return switch (period) {
            case 1 -> 1;
            case 15 -> 15;
            case 30 -> 30;
            case 60 -> 60;
            default -> 5;
        };
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
