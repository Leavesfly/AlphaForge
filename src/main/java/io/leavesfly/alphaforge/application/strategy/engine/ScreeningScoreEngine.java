package io.leavesfly.alphaforge.application.strategy.engine;

import io.leavesfly.alphaforge.application.strategy.model.ScreeningProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 智能选股打分引擎（AlphaSift 专用）。
 *
 * 读取策略 YAML 中 screening 段配置，对单只股票的实时行情计算适配分数。
 * 分数越高表示越符合该选股策略，由 AlphaSiftScreeningEngine 排序后返回 Top N。
 *
 * 与另两个引擎的分工：
 * - BacktestSignalEngine：历史 K 线 → 买/卖信号（backtest 段）
 * - 本引擎：实时行情 → 数值评分（screening 段）
 * - CompositeScoringEngine：多策略加权（scoring 段）
 *
 * 支持的规则写法（见 definitions/{category}/*.yaml）：
 * - 指标规则：metric + operator + threshold + weight，如 PE 越低分越高
 * - 条件公式：when: change_pct_gt + formula，如涨幅越大分越高
 * - 计算指标：peg 由 PE 与涨跌幅估算
 * - 兜底评分：行情缺字段时，按股票代码生成确定性伪随机分
 */
@Component
public class ScreeningScoreEngine {

    /**
     * 对单只股票按策略配置打分。
     *
     * @param definition 已从 YAML 加载的策略定义（须含 {@code screening} 段）
     * @param stockCode  股票代码，仅用于兜底评分
     * @param quote      实时行情，常见字段：pe、pb、change_pct、dividend_yield
     * @return 策略适配分；不满足任何规则且无法兜底时返回 0（表示不入选）
     */
    public double score(StrategyDefinition definition, String stockCode, Map<String, Object> quote) {
        ScreeningProfile profile = definition.getScreening();
        if (profile == null) {
            return 0;
        }

        double total = 0;
        for (Map<String, Object> rule : profile.getScoringRules()) {
            // 优先匹配「条件公式」规则（如动量策略），命中即返回，不再累加
            Double ruleScore = evaluateRule(rule, quote, profile.getParameters());
            if (ruleScore != null) {
                total = ruleScore;
                break;
            }
            // 否则按「指标规则」累加（如双低策略 PE 分 + PB 分）
            if (rule.containsKey("metric")) {
                total += evaluateMetricRule(rule, quote, profile.getParameters());
            }
        }

        if (total > 0) {
            return total;
        }
        return 0;
    }

    /**
     * 根据分数生成中文推荐理由。
     * 文案来自 YAML reason_templates.high / low，按策略 id 与分数阈值选用。
     */
    public String reason(StrategyDefinition definition, double score) {
        if (score <= 0) {
            return "data_unavailable";
        }
        Map<String, String> templates = definition.getScreening().getReasonTemplates();
        if (templates.isEmpty()) {
            return "策略推荐";
        }
        // 各策略「高分/低分」的分界线（与业务语义对应，非 YAML 配置项）
        double threshold = switch (definition.getId()) {
            case "dual_low" -> 60;
            case "value_growth" -> 50;
            case "momentum" -> 70;
            case "dividend" -> 50;
            default -> 60;
        };
        return score > threshold ? templates.getOrDefault("high", "策略推荐")
                : templates.getOrDefault("low", "策略推荐");
    }

    // ── 条件公式规则（when + formula）────────────────────────────────

    /**
     * 评估「条件触发 + 公式计分」类规则。
     * 示例：when=change_pct_gt, threshold=1.5, formula="50 + change_pct * 8"
     * 表示涨幅大于 1.5% 时，得分 = 50 + 涨幅 × 8。
     *
     * @return 命中时返回分数；非此类规则或条件未满足时返回 null
     */
    private Double evaluateRule(Map<String, Object> rule, Map<String, Object> quote, Map<String, Object> parameters) {
        String when = stringVal(rule.get("when"));
        if (when.isEmpty()) {
            return null;
        }
        double changePct = metric(quote, "change_pct", "pct_change");
        double threshold = doubleVal(rule.get("threshold"), doubleParam(parameters, "strong_change_pct", 1.5));
        if ("change_pct_gt".equals(when) && changePct > threshold) {
            return evalFormula(stringVal(rule.get("formula")), changePct);
        }
        return null;
    }

    // ── 指标规则（metric + operator + weight）────────────────────────

    /**
     * 评估「单一指标 + 比较符 + 权重」类规则。
     * - lt（小于阈值）：得分 = (阈值 - 实际值) × 权重，适用于 PE、PB 等越低越好
     * - gt（大于阈值）：得分 = 实际值 × 权重，适用于股息率等越高越好
     */
    private double evaluateMetricRule(Map<String, Object> rule, Map<String, Object> quote,
                                      Map<String, Object> parameters) {
        String metricName = stringVal(rule.get("metric"));
        double value = resolveMetric(metricName, quote, parameters);
        if (value <= 0 && !"peg".equals(metricName)) {
            return 0;
        }
        String operator = stringVal(rule.get("operator"));
        double threshold = doubleVal(rule.get("threshold"), 0);
        double weight = doubleVal(rule.get("weight"), 1);
        return switch (operator) {
            case "lt" -> value > 0 && value < threshold ? (threshold - value) * weight : 0;
            case "gt" -> value > threshold ? value * weight : 0;
            default -> 0;
        };
    }

    /**
     * 从行情中提取指标值；部分指标需现场计算。
     * - peg = PE ÷ max(涨跌幅 + growth_estimate_offset, 5)
     * - dividend_yield 兼容字段名 dy
     * - 其余指标按字段名或 字段名_ratio 查找
     */
    private double resolveMetric(String metricName, Map<String, Object> quote, Map<String, Object> parameters) {
        if ("peg".equals(metricName)) {
            double pe = metric(quote, "pe", "pe_ratio");
            if (pe <= 0) {
                return 0;
            }
            double changePct = metric(quote, "change_pct", "pct_change");
            double offset = doubleParam(parameters, "growth_estimate_offset", 15);
            return pe / Math.max(changePct + offset, 5);
        }
        if ("dividend_yield".equals(metricName)) {
            return metric(quote, "dividend_yield", "dy");
        }
        return metric(quote, metricName, metricName + "_ratio");
    }

    // ── 兜底评分（行情字段缺失时）────────────────────────────────────

    /**
     * 当主规则得分为 0 且行情缺少关键字段时，生成确定性兜底分。
     * 同一股票代码每次结果相同（基于 hash），用于数据源未返回 PE/PB 时的降级展示。
     */
    private double fallbackScore(ScreeningProfile profile, String stockCode, Map<String, Object> quote) {
        Map<String, Object> fallback = profile.getFallback();
        if (fallback.isEmpty()) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        List<String> metrics = (List<String>) fallback.get("metrics");
        if (metrics != null) {
            // 任一关键指标有值则不走兜底，直接淘汰（得 0 分）
            for (String metric : metrics) {
                double value = "dividend_yield".equals(metric)
                        ? metric(quote, "dividend_yield", "dy")
                        : metric(quote, metric, metric + "_ratio");
                if (value > 0) {
                    return 0;
                }
            }
        }
        int min = intVal(fallback.get("score_min"), 30);
        int max = intVal(fallback.get("score_max"), 90);
        return simulateScore(stockCode, min, max);
    }

    // ── 工具方法 ────────────────────────────────────────────────────

    /** 解析简单算术公式，支持 change_pct 占位符与 a + b * c 形式 */
    private double evalFormula(String formula, double changePct) {
        if (formula.isEmpty()) {
            return 0;
        }
        String expr = formula.replace("change_pct", String.valueOf(changePct)).replace(" ", "");
        double sum = 0;
        for (String term : expr.split("\\+")) {
            if (term.contains("*")) {
                String[] parts = term.split("\\*");
                sum += Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
            } else {
                sum += Double.parseDouble(term);
            }
        }
        return sum;
    }

    /** 基于股票代码 hash 在 [min, max) 区间生成稳定伪随机分。 */
    private double simulateScore(String code, int min, int max) {
        int hash = Math.abs(code.hashCode());
        return min + (hash % Math.max(max - min, 1));
    }

    /** 从行情 Map 中按多个候选 key 依次取值，取不到返回 0。 */
    private double metric(Map<String, Object> quote, String... keys) {
        for (String key : keys) {
            Object val = quote.get(key);
            if (val instanceof Number number) {
                return number.doubleValue();
            }
        }
        return 0;
    }

    private double doubleParam(Map<String, Object> parameters, String key, double defaultValue) {
        return doubleVal(parameters.get(key), defaultValue);
    }

    private String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }
}
