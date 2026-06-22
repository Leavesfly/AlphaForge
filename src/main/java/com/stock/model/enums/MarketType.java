package com.stock.model.enums;

/**
 * 市场类型枚举
 * 支持A股、港股、美股、日股、韩股
 */
public enum MarketType {
    A("A", "A股", "CN"),
    HK("HK", "港股", "HK"),
    US("US", "美股", "US"),
    JP("JP", "日股", "JP"),
    KR("KR", "韩股", "KR");

    private final String code;
    private final String name;
    private final String region;

    MarketType(String code, String name, String region) {
        this.code = code;
        this.name = name;
        this.region = region;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRegion() { return region; }

    /**
     * 根据股票代码自动检测市场类型
     */
    public static MarketType detectFromCode(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) return A;
        stockCode = stockCode.toUpperCase();
        if (stockCode.startsWith("HK") || stockCode.endsWith(".HK")) return HK;
        if (stockCode.matches("^[A-Z]{1,5}$")) return US;
        if (stockCode.endsWith(".T") || stockCode.startsWith("JP")) return JP;
        if (stockCode.endsWith(".KS") || stockCode.startsWith("KR")) return KR;
        return A;
    }

    public static MarketType fromCode(String code) {
        for (MarketType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        return A;
    }
}
