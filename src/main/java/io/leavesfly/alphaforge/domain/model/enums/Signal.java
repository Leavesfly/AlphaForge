package io.leavesfly.alphaforge.domain.model.enums;

/**
 * 信号类型枚举
 * 对应Agent分析结果的交易信号
 */
public enum Signal {
    STRONG_BUY("strong_buy", "强烈买入", 5),
    BUY("buy", "买入", 4),
    WEAK_BUY("weak_buy", "偏多", 3),
    NEUTRAL("neutral", "中性", 2),
    WEAK_SELL("weak_sell", "偏空", 1),
    SELL("sell", "卖出", 0),
    STRONG_SELL("strong_sell", "强烈卖出", -1);

    private final String code;
    private final String label;
    private final int score;

    Signal(String code, String label, int score) {
        this.code = code;
        this.label = label;
        this.score = score;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public int getScore() { return score; }

    public static Signal fromCode(String code) {
        for (Signal s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return NEUTRAL;
    }

    public static Signal fromScore(double score) {
        if (score >= 4.5) return STRONG_BUY;
        if (score >= 3.5) return BUY;
        if (score >= 2.5) return WEAK_BUY;
        if (score >= 1.5) return NEUTRAL;
        if (score >= 0.5) return WEAK_SELL;
        if (score >= -0.5) return SELL;
        return STRONG_SELL;
    }
}
