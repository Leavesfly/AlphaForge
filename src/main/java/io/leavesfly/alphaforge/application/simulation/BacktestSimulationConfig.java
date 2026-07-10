package io.leavesfly.alphaforge.application.backtest;

import java.util.Map;

/**
 * 回测仿真参数：交易成本、T+1、滑点、涨跌停等。
 */
public class BacktestSimulationConfig {

    private ExecutionMode executionMode = ExecutionMode.NEXT_OPEN;
    private double commissionRate = 0.0003;
    private double stampTaxRate = 0.0005;
    private double slippageRate = 0.001;
    private boolean t1Enabled = true;
    private int lotSize = 100;
    private double minCommission = 5.0;
    private double limitPct = 10.0;
    private boolean checkLimitPrice = true;
    private boolean checkSuspension = true;

    public static BacktestSimulationConfig forStockCode(String stockCode) {
        BacktestSimulationConfig config = new BacktestSimulationConfig();
        if (stockCode == null || stockCode.isBlank()) {
            return config;
        }
        String code = stockCode.trim();
        if (isAShare(code)) {
            config.t1Enabled = true;
            config.lotSize = 100;
            config.stampTaxRate = 0.0005;
            config.limitPct = resolveLimitPct(code);
            return config;
        }
        config.t1Enabled = false;
        config.lotSize = 1;
        config.stampTaxRate = 0.0;
        config.minCommission = 0.0;
        return config;
    }

    public static BacktestSimulationConfig merge(BacktestSimulationConfig base, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return base;
        }
        BacktestSimulationConfig merged = copy(base);
        if (overrides.containsKey("execution_mode")) {
            merged.executionMode = ExecutionMode.valueOf(String.valueOf(overrides.get("execution_mode")).toUpperCase());
        }
        merged.commissionRate = doubleOverride(overrides, "commission_rate", merged.commissionRate);
        merged.stampTaxRate = doubleOverride(overrides, "stamp_tax_rate", merged.stampTaxRate);
        merged.slippageRate = doubleOverride(overrides, "slippage_rate", merged.slippageRate);
        merged.minCommission = doubleOverride(overrides, "min_commission", merged.minCommission);
        merged.limitPct = doubleOverride(overrides, "limit_pct", merged.limitPct);
        if (overrides.containsKey("t1_enabled")) {
            merged.t1Enabled = Boolean.parseBoolean(String.valueOf(overrides.get("t1_enabled")));
        }
        if (overrides.containsKey("lot_size")) {
            merged.lotSize = intOverride(overrides, "lot_size", merged.lotSize);
        }
        if (overrides.containsKey("check_limit_price")) {
            merged.checkLimitPrice = Boolean.parseBoolean(String.valueOf(overrides.get("check_limit_price")));
        }
        if (overrides.containsKey("check_suspension")) {
            merged.checkSuspension = Boolean.parseBoolean(String.valueOf(overrides.get("check_suspension")));
        }
        return merged;
    }

    private static BacktestSimulationConfig copy(BacktestSimulationConfig source) {
        BacktestSimulationConfig copy = new BacktestSimulationConfig();
        copy.executionMode = source.executionMode;
        copy.commissionRate = source.commissionRate;
        copy.stampTaxRate = source.stampTaxRate;
        copy.slippageRate = source.slippageRate;
        copy.t1Enabled = source.t1Enabled;
        copy.lotSize = source.lotSize;
        copy.minCommission = source.minCommission;
        copy.limitPct = source.limitPct;
        copy.checkLimitPrice = source.checkLimitPrice;
        copy.checkSuspension = source.checkSuspension;
        return copy;
    }

    private static boolean isAShare(String code) {
        return code.matches("^\\d{6}$");
    }

    private static double resolveLimitPct(String code) {
        if (code.startsWith("300") || code.startsWith("301") || code.startsWith("688")) {
            return 20.0;
        }
        if (code.startsWith("8") || code.startsWith("4")) {
            return 30.0;
        }
        return 10.0;
    }

    private static double doubleOverride(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private static int intOverride(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    public ExecutionMode getExecutionMode() { return executionMode; }
    public double getCommissionRate() { return commissionRate; }
    public double getStampTaxRate() { return stampTaxRate; }
    public double getSlippageRate() { return slippageRate; }
    public boolean isT1Enabled() { return t1Enabled; }
    public int getLotSize() { return lotSize; }
    public double getMinCommission() { return minCommission; }
    public double getLimitPct() { return limitPct; }
    public boolean isCheckLimitPrice() { return checkLimitPrice; }
    public boolean isCheckSuspension() { return checkSuspension; }
}
