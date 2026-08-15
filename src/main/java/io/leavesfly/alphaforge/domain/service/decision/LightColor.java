package io.leavesfly.alphaforge.domain.service.decision;

/**
 * 灯色 — 绿/黄/红/灰。
 *
 * <p>灰 = 数据不可用，诚实标注不猜测（区别于红灯的"已检查且不佳"）。</p>
 */
public enum LightColor {
    GREEN("绿"),
    YELLOW("黄"),
    RED("红"),
    GRAY("灰");

    private final String cn;

    LightColor(String cn) {
        this.cn = cn;
    }

    public String getCn() {
        return cn;
    }
}
