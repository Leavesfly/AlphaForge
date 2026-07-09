package io.leavesfly.alphaforge.application.service.screening;

import io.leavesfly.alphaforge.application.strategy.condition.FactorConditions;
import io.leavesfly.alphaforge.application.strategy.model.ScreeningProfile;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.factor.CrossSectionalOps;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选股池截面因子分位注入。
 *
 * <p>对策略 YAML 中声明的因子，拉取近期 K 线计算点时因子值，再在股票池内做
 * {@link CrossSectionalOps#rankNormalize}，写入 quote 的 {@code factor_{name}_rank}
 *（0~1，越高表示因子值在池内越靠前）。</p>
 */
@Component
public class CrossSectionalFactorEnricher {

    private static final Logger log = LoggerFactory.getLogger(CrossSectionalFactorEnricher.class);
    private static final int HISTORY_DAYS = 120;

    private final MarketDataPort marketData;

    public CrossSectionalFactorEnricher(MarketDataPort marketData) {
        this.marketData = marketData;
    }

    /**
     * 就地丰富 quotes：为每只股票写入所需因子的截面分位。
     *
     * @param definition 策略定义（从 rank/parameters 推断因子名）
     * @param quotes     code → 行情 Map（会被 put 新字段）
     */
    public void enrich(StrategyDefinition definition, Map<String, Map<String, Object>> quotes) {
        if (definition == null || quotes == null || quotes.isEmpty()) {
            return;
        }
        Set<String> factors = resolveFactorNames(definition);
        if (factors.isEmpty()) {
            return;
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(HISTORY_DAYS);
        Map<String, List<StockDailyData>> histories = new LinkedHashMap<>();
        for (String code : quotes.keySet()) {
            try {
                List<StockDailyData> hist = marketData.getHistoryData(code, start, end);
                if (hist != null && !hist.isEmpty()) {
                    histories.put(code, hist);
                }
            } catch (Exception e) {
                log.debug("截面因子历史拉取失败 {}: {}", code, e.getMessage());
            }
        }
        if (histories.size() < 2) {
            return;
        }

        List<String> codes = new ArrayList<>(histories.keySet());
        for (String factor : factors) {
            double[] values = new double[codes.size()];
            boolean[] valid = new boolean[codes.size()];
            int validCount = 0;
            for (int i = 0; i < codes.size(); i++) {
                List<StockDailyData> hist = histories.get(codes.get(i));
                double v = FactorConditions.compute(factor, hist, hist.size() - 1);
                if (!Double.isNaN(v)) {
                    values[i] = v;
                    valid[i] = true;
                    validCount++;
                }
            }
            if (validCount < 2) {
                continue;
            }
            // 仅对有效样本做排名，再写回
            double[] validValues = new double[validCount];
            int[] map = new int[validCount];
            int k = 0;
            for (int i = 0; i < codes.size(); i++) {
                if (valid[i]) {
                    validValues[k] = values[i];
                    map[k] = i;
                    k++;
                }
            }
            double[] ranks = CrossSectionalOps.rankNormalize(validValues);
            String key = "factor_" + factor + "_rank";
            for (int j = 0; j < validCount; j++) {
                String code = codes.get(map[j]);
                Map<String, Object> quote = quotes.get(code);
                if (quote != null) {
                    quote.put(key, ranks[j]);
                    quote.put("factor_" + factor, validValues[j]);
                }
            }
        }
    }

    /** 从 screening parameters.cross_section_factors 或 scoring/rank 规则推断因子名 */
    @SuppressWarnings("unchecked")
    static Set<String> resolveFactorNames(StrategyDefinition definition) {
        Set<String> names = new LinkedHashSet<>();
        ScreeningProfile screening = definition.getScreening();
        if (screening != null) {
            Object configured = screening.getParameters().get("cross_section_factors");
            if (configured instanceof List<?> list) {
                for (Object item : list) {
                    String name = String.valueOf(item);
                    if (FactorConditions.supports(name)) {
                        names.add(name);
                    }
                }
            } else if (configured instanceof String csv) {
                for (String part : csv.split(",")) {
                    String name = part.trim();
                    if (FactorConditions.supports(name)) {
                        names.add(name);
                    }
                }
            }
            for (Map<String, Object> rule : screening.getScoringRules()) {
                String metric = rule.get("metric") != null ? String.valueOf(rule.get("metric")) : "";
                if (metric.startsWith("factor_") && metric.endsWith("_rank")) {
                    String name = metric.substring("factor_".length(), metric.length() - "_rank".length());
                    if (FactorConditions.supports(name)) {
                        names.add(name);
                    }
                }
                String when = rule.get("when") != null ? String.valueOf(rule.get("when")) : "";
                if ("factor_rank_gte".equals(when) || "factor_rank_lte".equals(when)) {
                    String name = rule.get("factor") != null ? String.valueOf(rule.get("factor")) : "";
                    if (FactorConditions.supports(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }
}
