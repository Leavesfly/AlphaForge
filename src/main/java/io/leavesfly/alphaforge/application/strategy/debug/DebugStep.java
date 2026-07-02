package io.leavesfly.alphaforge.application.strategy.debug;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 策略调试单步记录：记录某一天的条件评估详情
 */
public class DebugStep {

    private LocalDate date;
    private int index;
    private double openPrice;
    private double closePrice;
    private long volume;
    /** 信号：1=买入，-1=卖出，0=无信号 */
    private int signal;
    /** 是否持仓 */
    private boolean holding;
    /** 入场价 */
    private double entryPrice;
    /** 各入场条件的命中详情 */
    private List<ConditionResult> entryConditions;
    /** 各出场条件的命中详情 */
    private List<ConditionResult> exitConditions;

    public static class ConditionResult {
        private String type;
        private boolean matched;
        private String detail;

        public ConditionResult(String type, boolean matched, String detail) {
            this.type = type;
            this.matched = matched;
            this.detail = detail;
        }

        public String getType() { return type; }
        public boolean isMatched() { return matched; }
        public String getDetail() { return detail; }
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public double getOpenPrice() { return openPrice; }
    public void setOpenPrice(double openPrice) { this.openPrice = openPrice; }

    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public int getSignal() { return signal; }
    public void setSignal(int signal) { this.signal = signal; }

    public boolean isHolding() { return holding; }
    public void setHolding(boolean holding) { this.holding = holding; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public List<ConditionResult> getEntryConditions() { return entryConditions; }
    public void setEntryConditions(List<ConditionResult> entryConditions) { this.entryConditions = entryConditions; }

    public List<ConditionResult> getExitConditions() { return exitConditions; }
    public void setExitConditions(List<ConditionResult> exitConditions) { this.exitConditions = exitConditions; }
}
