package io.leavesfly.alphaforge.domain.model.enums;

/**
 * 数据源提供者枚举
 */
public enum DataProviderType {
    EFINANCE("efinance", "efinance数据源", true),
    AKSHARE("akshare", "AKShare数据源", true),
    TUSHARE("tushare", "Tushare数据源", true),
    PYTDX("pytdx", "通达信数据源", true),
    BAOSTOCK("baostock", "BaoStock数据源", true),
    YFINANCE("yfinance", "Yahoo Finance数据源", true),
    LONGBRIDGE("longbridge", "长桥数据源", true),
    ALPHAVANTAGE("alphavantage", "Alpha Vantage数据源", true),
    FINNHUB("finnhub", "Finnhub数据源", true),
    TENCENT("tencent", "腾讯数据源", true),
    TICKFLOW("tickflow", "TickFlow数据源", true);

    private final String code;
    private final String name;
    private final boolean supportsRealtime;

    DataProviderType(String code, String name, boolean supportsRealtime) {
        this.code = code;
        this.name = name;
        this.supportsRealtime = supportsRealtime;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isSupportsRealtime() { return supportsRealtime; }

    public static DataProviderType fromCode(String code) {
        for (DataProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        return AKSHARE;
    }
}
