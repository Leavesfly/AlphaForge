package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据源相关配置
 *
 * 支持的数据源及其对应环境变量：
 * - DATA_PROVIDER: 数据源类型（auto / efinance / tushare / ...)
 * - TUSHARE_TOKEN: Tushare Pro API Token
 * - LONGBRIDGE_APP_KEY / LONGBRIDGE_APP_SECRET / LONGBRIDGE_ACCESS_TOKEN: 长桥证件
 * - ALPHAVANTAGE_API_KEY: Alpha Vantage API Key
 * - FINNHUB_API_KEY: Finnhub API Key
 * - TICKFLOW_API_KEY / TICKFLOW_BASE_URL: TickFlow 证件
 * - CROSS_CHECK_MODE: 多源交叉校验模式 off / warn / reject（默认 warn）
 * - CROSS_CHECK_SAMPLE_DAYS / CROSS_CHECK_CLOSE_TOLERANCE / CROSS_CHECK_OHLC_TOLERANCE / CROSS_CHECK_REJECT_RATIO
 */
@Component
public class DataProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(DataProviderConfig.class);

    private final EnvVarProvider envVarProvider;

    private String dataProvider = "auto";
    private String tushareToken = "";
    private String longbridgeAppKey = "";
    private String longbridgeAppSecret = "";
    private String longbridgeAccessToken = "";
    private String alphavantageApiKey = "";
    private String finnhubApiKey = "";
    private String tickflowApiKey = "";
    private String tickflowBaseUrl = "https://api.tickflow.org";

    /** 交叉校验模式: off / warn / reject */
    private String crossCheckMode = "warn";
    private int crossCheckSampleDays = 20;
    private double crossCheckCloseTolerance = 0.005;
    private double crossCheckOhlcTolerance = 0.01;
    private double crossCheckRejectRatio = 0.10;

    public DataProviderConfig(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider;
    }

    @PostConstruct
    public void init() {
        dataProvider         = envVarProvider.get("DATA_PROVIDER",          "auto");
        tushareToken         = envVarProvider.get("TUSHARE_TOKEN",           "");
        longbridgeAppKey     = envVarProvider.get("LONGBRIDGE_APP_KEY",      "");
        longbridgeAppSecret  = envVarProvider.get("LONGBRIDGE_APP_SECRET",   "");
        longbridgeAccessToken= envVarProvider.get("LONGBRIDGE_ACCESS_TOKEN", "");
        alphavantageApiKey   = envVarProvider.get("ALPHAVANTAGE_API_KEY",    "");
        finnhubApiKey        = envVarProvider.get("FINNHUB_API_KEY",         "");
        tickflowApiKey       = envVarProvider.get("TICKFLOW_API_KEY",        "");
        tickflowBaseUrl      = envVarProvider.get("TICKFLOW_BASE_URL",       "https://api.tickflow.org");
        crossCheckMode       = normalizeCrossCheckMode(envVarProvider.get("CROSS_CHECK_MODE", "warn"));
        crossCheckSampleDays = Math.max(1, envVarProvider.getInt("CROSS_CHECK_SAMPLE_DAYS", 20));
        crossCheckCloseTolerance = envVarProvider.getDouble("CROSS_CHECK_CLOSE_TOLERANCE", 0.005);
        crossCheckOhlcTolerance = envVarProvider.getDouble("CROSS_CHECK_OHLC_TOLERANCE", 0.01);
        crossCheckRejectRatio = envVarProvider.getDouble("CROSS_CHECK_REJECT_RATIO", 0.10);
        log.info("数据源配置加载完成: provider={}, tushare={}, longbridge={}, tickflow={}, crossCheck={}",
                dataProvider,
                tushareToken.isEmpty() ? "未配置" : "已配置",
                longbridgeAppKey.isEmpty() ? "未配置" : "已配置",
                tickflowApiKey.isEmpty() ? "未配置" : "已配置",
                crossCheckMode);
    }

    private static String normalizeCrossCheckMode(String mode) {
        if (mode == null || mode.isBlank()) return "warn";
        String m = mode.trim().toLowerCase();
        return switch (m) {
            case "off", "warn", "reject" -> m;
            default -> "warn";
        };
    }

    // Getters
    public String getDataProvider()         { return dataProvider; }
    public String getTushareToken()          { return tushareToken; }
    public String getLongbridgeAppKey()      { return longbridgeAppKey; }
    public String getLongbridgeAppSecret()   { return longbridgeAppSecret; }
    public String getLongbridgeAccessToken() { return longbridgeAccessToken; }
    public String getAlphavantageApiKey()    { return alphavantageApiKey; }
    public String getFinnhubApiKey()         { return finnhubApiKey; }
    public String getTickflowApiKey()        { return tickflowApiKey; }
    public String getTickflowBaseUrl()       { return tickflowBaseUrl; }
    public String getCrossCheckMode()        { return crossCheckMode; }
    public int getCrossCheckSampleDays()     { return crossCheckSampleDays; }
    public double getCrossCheckCloseTolerance() { return crossCheckCloseTolerance; }
    public double getCrossCheckOhlcTolerance()  { return crossCheckOhlcTolerance; }
    public double getCrossCheckRejectRatio()    { return crossCheckRejectRatio; }

    public boolean isCrossCheckEnabled() {
        return !"off".equalsIgnoreCase(crossCheckMode);
    }

    public boolean isCrossCheckRejectMode() {
        return "reject".equalsIgnoreCase(crossCheckMode);
    }
}
