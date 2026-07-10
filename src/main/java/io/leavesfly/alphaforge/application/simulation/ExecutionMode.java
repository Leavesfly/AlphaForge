package io.leavesfly.alphaforge.application.backtest;

/**
 * 回测成交模式。
 */
public enum ExecutionMode {
    /** 信号在当日收盘判定，次日开盘价成交（推荐，避免未来函数） */
    NEXT_OPEN,
    /** 信号当日收盘价成交（简化模式，仅用于对比） */
    CLOSE
}
