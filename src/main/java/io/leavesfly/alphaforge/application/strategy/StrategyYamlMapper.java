package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.application.strategy.model.BacktestProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScoringProfile;
import io.leavesfly.alphaforge.application.strategy.model.ScreeningProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略 YAML → {@link StrategyDefinition} 统一映射。
 *
 * <p>支持两套写法（可并存；显式旧段优先，缺失时由 {@code rules} 补齐）：</p>
 * <ul>
 *   <li>旧三段：{@code backtest} / {@code scoring} / {@code screening}</li>
 *   <li>统一 DSL：{@code rules.signal} / {@code rules.score} / {@code rules.rank}</li>
 * </ul>
 *
 * <pre>
 * rules:
 *   parameters: { ... }
 *   param_space: { ... }
 *   signal:                    # → BacktestProfile
 *     position_size: 0.8
 *     entry: [ { type: ... } ]
 *     exit:  [ { type: ... } ]
 *   score:                     # → ScoringProfile
 *     weight: 18
 *     conditions: { key: value }           # 旧扁平写法
 *     when: [ { type: ..., ... } ]         # 统一 type 条件，映射为评分 key
 *   rank:                      # → ScreeningProfile
 *     scoring_rules: [ ... ]
 * </pre>
 */
public final class StrategyYamlMapper {

    private StrategyYamlMapper() {
    }

    @SuppressWarnings("unchecked")
    public static StrategyDefinition mapDefinition(Map<String, Object> raw) {
        StrategyDefinition definition = new StrategyDefinition();
        definition.setId(stringVal(raw.get("id"), ""));
        definition.setSchemaVersion(intVal(raw.get("schema_version"), 1));
        definition.setLabel(stringVal(raw.get("label"), definition.getId()));
        definition.setDescription(stringVal(raw.get("description"), ""));
        definition.setCategory(stringVal(raw.get("category"), ""));
        definition.setRiskLevel(stringVal(raw.get("risk_level"), "medium"));
        definition.setApplicableMarket(parseStringList(raw.get("applicable_market")));
        definition.setApplicableCap(parseStringList(raw.get("applicable_cap")));
        definition.setTags(parseStringList(raw.get("tags")));

        BacktestProfile backtest = null;
        ScoringProfile scoring = null;
        ScreeningProfile screening = null;

        if (raw.get("backtest") instanceof Map<?, ?> backtestRaw) {
            backtest = mapBacktest((Map<String, Object>) backtestRaw);
        }
        if (raw.get("scoring") instanceof Map<?, ?> scoringRaw) {
            scoring = mapScoring((Map<String, Object>) scoringRaw);
        }
        if (raw.get("screening") instanceof Map<?, ?> screeningRaw) {
            screening = mapScreening((Map<String, Object>) screeningRaw);
        }

        if (raw.get("rules") instanceof Map<?, ?> rulesRaw) {
            Map<String, Object> rules = (Map<String, Object>) rulesRaw;
            if (backtest == null && rules.get("signal") instanceof Map<?, ?>) {
                backtest = mapSignalRules(rules, (Map<String, Object>) rules.get("signal"));
            }
            if (scoring == null && rules.get("score") instanceof Map<?, ?>) {
                scoring = mapScoreRules((Map<String, Object>) rules.get("score"));
            }
            if (screening == null && rules.get("rank") instanceof Map<?, ?>) {
                screening = mapRankRules(rules, (Map<String, Object>) rules.get("rank"));
            }
        }

        definition.setBacktest(backtest);
        definition.setScoring(scoring);
        definition.setScreening(screening);
        return definition;
    }

    /** 从已解析定义推断能力列表（自定义策略用） */
    public static List<String> inferCapabilities(StrategyDefinition definition) {
        List<String> caps = new ArrayList<>();
        if (definition.getBacktest() != null) {
            caps.add("backtest");
        }
        if (definition.getScreening() != null) {
            caps.add("screening");
        }
        if (definition.getScoring() != null) {
            caps.add("scoring");
        }
        return caps;
    }

    @SuppressWarnings("unchecked")
    public static BacktestProfile mapBacktest(Map<String, Object> raw) {
        BacktestProfile profile = new BacktestProfile();
        if (raw.get("parameters") instanceof Map<?, ?> params) {
            profile.setParameters((Map<String, Object>) params);
        }
        if (raw.get("entry_conditions") instanceof List<?> entries) {
            profile.setEntryConditions(entries.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        if (raw.get("exit_conditions") instanceof List<?> exits) {
            profile.setExitConditions(exits.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        profile.setPositionSize(doubleVal(raw.get("position_size"), 0.95));
        if (raw.get("position_sizing") instanceof Map<?, ?> sizing) {
            profile.setPositionSizing((Map<String, Object>) sizing);
        }
        if (raw.get("simulation") instanceof Map<?, ?> simulation) {
            profile.setSimulation((Map<String, Object>) simulation);
        }
        if (raw.get("param_space") instanceof Map<?, ?> paramSpace) {
            profile.setParamSpace(parseParamSpace((Map<String, Object>) paramSpace));
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    public static ScreeningProfile mapScreening(Map<String, Object> raw) {
        ScreeningProfile profile = new ScreeningProfile();
        if (raw.get("parameters") instanceof Map<?, ?> params) {
            profile.setParameters((Map<String, Object>) params);
        }
        if (raw.get("scoring_rules") instanceof List<?> rules) {
            profile.setScoringRules(rules.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        if (raw.get("reason_templates") instanceof Map<?, ?> templates) {
            profile.setReasonTemplates((Map<String, String>) templates);
        }
        if (raw.get("fallback") instanceof Map<?, ?> fallback) {
            profile.setFallback((Map<String, Object>) fallback);
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    public static ScoringProfile mapScoring(Map<String, Object> raw) {
        ScoringProfile profile = new ScoringProfile();
        profile.setScoreWeight(intVal(raw.get("score_weight"), 0));
        if (raw.get("conditions") instanceof Map<?, ?> conditions) {
            profile.setConditions((Map<String, Object>) conditions);
        }
        profile.setLabel(stringVal(raw.get("label"), null));
        profile.setAutoDecay(Boolean.TRUE.equals(raw.get("auto_decay")));
        profile.setDecayWindow(intVal(raw.get("decay_window"), 30));
        profile.setMinWeight(intVal(raw.get("min_weight"), 5));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private static BacktestProfile mapSignalRules(Map<String, Object> rules, Map<String, Object> signal) {
        BacktestProfile profile = new BacktestProfile();
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (rules.get("parameters") instanceof Map<?, ?> shared) {
            parameters.putAll((Map<String, Object>) shared);
        }
        if (signal.get("parameters") instanceof Map<?, ?> local) {
            parameters.putAll((Map<String, Object>) local);
        }
        profile.setParameters(parameters);

        if (signal.get("entry") instanceof List<?> entries) {
            profile.setEntryConditions(entries.stream().map(StrategyYamlMapper::asConditionMap).toList());
        } else if (signal.get("entry_conditions") instanceof List<?> entries) {
            profile.setEntryConditions(entries.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        if (signal.get("exit") instanceof List<?> exits) {
            profile.setExitConditions(exits.stream().map(StrategyYamlMapper::asConditionMap).toList());
        } else if (signal.get("exit_conditions") instanceof List<?> exits) {
            profile.setExitConditions(exits.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        profile.setPositionSize(doubleVal(signal.get("position_size"), 0.95));
        Object sizing = signal.containsKey("position_sizing")
                ? signal.get("position_sizing") : rules.get("position_sizing");
        if (sizing instanceof Map<?, ?> sizingMap) {
            profile.setPositionSizing((Map<String, Object>) sizingMap);
        }
        if (signal.get("simulation") instanceof Map<?, ?> simulation) {
            profile.setSimulation((Map<String, Object>) simulation);
        }
        Object paramSpace = signal.containsKey("param_space") ? signal.get("param_space") : rules.get("param_space");
        if (paramSpace instanceof Map<?, ?> space) {
            profile.setParamSpace(parseParamSpace((Map<String, Object>) space));
        }
        return profile;
    }

    @SuppressWarnings("unchecked")
    private static ScoringProfile mapScoreRules(Map<String, Object> score) {
        ScoringProfile profile = new ScoringProfile();
        int weight = intVal(score.get("weight"), intVal(score.get("score_weight"), 0));
        profile.setScoreWeight(weight);
        profile.setLabel(stringVal(score.get("label"), null));
        profile.setAutoDecay(Boolean.TRUE.equals(score.get("auto_decay")));
        profile.setDecayWindow(intVal(score.get("decay_window"), 30));
        profile.setMinWeight(intVal(score.get("min_weight"), 5));

        Map<String, Object> conditions = new LinkedHashMap<>();
        if (score.get("conditions") instanceof Map<?, ?> flat) {
            conditions.putAll((Map<String, Object>) flat);
        }
        if (score.get("when") instanceof List<?> whenList) {
            for (Object item : whenList) {
                if (item instanceof Map<?, ?>) {
                    conditions.putAll(typedConditionToScoringKeys(asConditionMap(item)));
                }
            }
        }
        profile.setConditions(conditions);
        return profile;
    }

    @SuppressWarnings("unchecked")
    private static ScreeningProfile mapRankRules(Map<String, Object> rules, Map<String, Object> rank) {
        ScreeningProfile profile = new ScreeningProfile();
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (rules.get("parameters") instanceof Map<?, ?> shared) {
            parameters.putAll((Map<String, Object>) shared);
        }
        if (rank.get("parameters") instanceof Map<?, ?> local) {
            parameters.putAll((Map<String, Object>) local);
        }
        profile.setParameters(parameters);
        if (rank.get("scoring_rules") instanceof List<?> scoringRules) {
            profile.setScoringRules(scoringRules.stream().map(StrategyYamlMapper::asConditionMap).toList());
        }
        if (rank.get("reason_templates") instanceof Map<?, ?> templates) {
            profile.setReasonTemplates((Map<String, String>) templates);
        }
        if (rank.get("fallback") instanceof Map<?, ?> fallback) {
            profile.setFallback((Map<String, Object>) fallback);
        }
        return profile;
    }

    /**
     * 将统一 type 条件映射为综合评分扁平 key，便于 CompositeScoringEngine 复用。
     */
    public static Map<String, Object> typedConditionToScoringKeys(Map<String, Object> condition) {
        String type = stringVal(condition.get("type"), "");
        Map<String, Object> keys = new LinkedHashMap<>();
        switch (type) {
            case "price_near_low" -> keys.put("price_near_low", true);
            case "consecutive_volume_days" ->
                    keys.put("consecutive_days", intVal(condition.get("days"), 2));
            case "yang_covers_yin" ->
                    keys.put("yang_covers_yin_count", intVal(condition.get("yin_count"), 3));
            case "volume_amplify" ->
                    keys.put("volume_amplify", doubleVal(condition.get("min_ratio"), 1.5));
            case "volume_breakout", "volume_ratio" ->
                    keys.put("volume_ratio_min", doubleVal(condition.get("multiple"), 2.0));
            case "macd_golden_cross" -> keys.put("divergence", true);
            case "boll_upper_break" -> keys.put("center_break", true);
            case "ma_arrangement" -> keys.put("ma_alignment",
                    stringVal(condition.get("direction"), "bullish"));
            case "trend_above" -> keys.put("trend_up", true);
            case "volume_shrink" ->
                    keys.put("volume_shrink_ratio", doubleVal(condition.get("ratio"), 0.5));
            case "near_box_low", "price_near_support" -> keys.put("price_near_low", true);
            case "event_trigger" -> keys.put("has_major_event", true);
            case "fundamental_filter" -> {
                if (condition.containsKey("revenue_growth_min")) {
                    keys.put("revenue_growth_min", condition.get("revenue_growth_min"));
                }
                if (condition.containsKey("roe_min")) {
                    keys.put("roe_min", condition.get("roe_min"));
                }
                if (condition.containsKey("profit_growth_min")) {
                    keys.put("profit_growth_min", condition.get("profit_growth_min"));
                }
            }
            case "factor" -> {
                String name = stringVal(condition.get("name"), stringVal(condition.get("factor"), ""));
                if (!name.isBlank()) {
                    if (condition.containsKey("min")) {
                        keys.put("factor_" + name + "_min", condition.get("min"));
                    }
                    if (condition.containsKey("max")) {
                        keys.put("factor_" + name + "_max", condition.get("max"));
                    }
                    if (!condition.containsKey("min") && !condition.containsKey("max")) {
                        keys.put("factor_" + name + "_present", true);
                    }
                }
            }
            case "channel_breakout" -> keys.put("channel_breakout", true);
            case "channel_breakdown" -> keys.put("channel_breakdown", true);
            case "boll_lower_touch" -> keys.put("boll_lower_touch", true);
            case "boll_upper_touch" -> keys.put("boll_upper_touch", true);
            case "boll_mid_reclaim" -> keys.put("boll_mid_reclaim", true);
            case "momentum_up" ->
                    keys.put("momentum_up_min", doubleVal(condition.get("min_change"), 1.5));
            default -> {
                // 未知 type：若带 value 则用 type 本身作 key
                if (condition.containsKey("value")) {
                    keys.put(type, condition.get("value"));
                }
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asConditionMap(Object item) {
        return item instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, List<Object>> parseParamSpace(Map<String, Object> paramSpace) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paramSpace.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                result.put(entry.getKey(), new ArrayList<>(list));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return List.of(str.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return Collections.emptyList();
    }

    private static String stringVal(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }
}
