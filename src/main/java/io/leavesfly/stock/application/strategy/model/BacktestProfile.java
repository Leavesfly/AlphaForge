package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 回测配置段，对应 YAML 中 backtest: 节点。
 */
public class BacktestProfile {

    /** 策略参数，如 fast_period、volume_multiple */
    private Map<String, Object> parameters = Collections.emptyMap();
    /** 入场条件列表，全部满足时买入（AND 逻辑） */
    private List<Map<String, Object>> entryConditions = Collections.emptyList();
    /** 出场条件列表，满足任一即卖出（OR 逻辑） */
    private List<Map<String, Object>> exitConditions = Collections.emptyList();
    /** 买入仓位比例，0~1 之间的小数，如 0.95 表示 95% */
    private double positionSize = 0.95;
    /** 回测仿真参数覆盖，如 commission_rate、slippage_rate */
    private Map<String, Object> simulation = Collections.emptyMap();

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? parameters : Collections.emptyMap();
    }

    public List<Map<String, Object>> getEntryConditions() { return entryConditions; }
    public void setEntryConditions(List<Map<String, Object>> entryConditions) {
        this.entryConditions = entryConditions != null ? entryConditions : Collections.emptyList();
    }

    public List<Map<String, Object>> getExitConditions() { return exitConditions; }
    public void setExitConditions(List<Map<String, Object>> exitConditions) {
        this.exitConditions = exitConditions != null ? exitConditions : Collections.emptyList();
    }

    public double getPositionSize() { return positionSize; }
    public void setPositionSize(double positionSize) { this.positionSize = positionSize; }

    public Map<String, Object> getSimulation() { return simulation; }
    public void setSimulation(Map<String, Object> simulation) {
        this.simulation = simulation != null ? simulation : Collections.emptyMap();
    }
}
