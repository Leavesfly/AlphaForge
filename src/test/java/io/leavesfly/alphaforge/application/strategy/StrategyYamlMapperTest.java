package io.leavesfly.alphaforge.application.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrategyYamlMapper 统一 rules DSL")
class StrategyYamlMapperTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    @DisplayName("rules.signal/score 应映射为 backtest + scoring")
    void shouldMapUnifiedRules() throws Exception {
        String content = """
                id: demo
                label: 演示
                category: technical
                rules:
                  parameters:
                    yin_count: 3
                  signal:
                    position_size: 0.85
                    entry:
                      - type: yang_covers_yin
                        yin_count: 3
                    exit:
                      - type: stop_loss
                        pct: -8
                  score:
                    weight: 14
                    when:
                      - type: yang_covers_yin
                        yin_count: 3
                      - type: volume_amplify
                        min_ratio: 1.5
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = yaml.readValue(content, Map.class);
        StrategyDefinition def = StrategyYamlMapper.mapDefinition(raw);

        assertTrue(def.hasBacktest());
        assertEquals(0.85, def.getBacktest().getPositionSize(), 0.001);
        assertEquals(1, def.getBacktest().getEntryConditions().size());
        assertEquals("yang_covers_yin", def.getBacktest().getEntryConditions().get(0).get("type"));
        assertEquals(3, def.getBacktest().getParameters().get("yin_count"));

        assertTrue(def.hasScoring());
        assertEquals(14, def.getScoring().getScoreWeight());
        assertEquals(3, def.getScoring().getConditions().get("yang_covers_yin_count"));
        assertEquals(1.5, ((Number) def.getScoring().getConditions().get("volume_amplify")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("旧三段 YAML 仍可解析")
    void shouldKeepLegacySections() throws Exception {
        String content = """
                id: legacy
                label: 旧格式
                category: technical
                backtest:
                  position_size: 0.9
                  entry_conditions:
                    - type: momentum_up
                      min_change: 1.5
                  exit_conditions:
                    - type: stop_loss
                      pct: -8
                scoring:
                  score_weight: 10
                  conditions:
                    trend_up: true
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = yaml.readValue(content, Map.class);
        StrategyDefinition def = StrategyYamlMapper.mapDefinition(raw);
        assertTrue(def.hasBacktest());
        assertTrue(def.hasScoring());
        assertEquals(0.9, def.getBacktest().getPositionSize(), 0.001);
        assertTrue((Boolean) def.getScoring().getConditions().get("trend_up"));
    }

    @Test
    @DisplayName("position_sizing 应映射到 BacktestProfile")
    void shouldMapPositionSizing() throws Exception {
        String content = """
                id: atr_demo
                label: ATR仓位
                category: technical
                rules:
                  position_sizing:
                    mode: atr
                    risk_fraction: 0.01
                    atr_period: 20
                    atr_multiplier: 2.0
                  signal:
                    position_size: 0.8
                    entry:
                      - type: channel_breakout
                        lookback: 20
                    exit:
                      - type: atr_stop
                        period: 20
                        multiplier: 2.0
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = yaml.readValue(content, Map.class);
        StrategyDefinition def = StrategyYamlMapper.mapDefinition(raw);
        assertEquals(0.8, def.getBacktest().getPositionSize(), 0.001);
        assertEquals("atr", def.getBacktest().getPositionSizing().get("mode"));
        assertEquals(0.01, ((Number) def.getBacktest().getPositionSizing().get("risk_fraction")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("全部 23 个策略应经 rules DSL 加载且可用")
    void allStrategiesShouldLoadFromRulesDsl() {
        StrategyCatalog catalog = StrategyCatalogLoader.createAndLoad().getCatalog();
        assertEquals(23, catalog.listAll().size());
        assertEquals(23, catalog.listByCapability("backtest").size());

        for (StrategyDefinition s : catalog.listAll()) {
            assertTrue(s.hasBacktest(), s.getId() + " 应有 signal→backtest");
            assertTrue(s.isAvailable(), s.getId() + ": " + s.getUnavailableReason());
            assertFalse(s.getBacktest().getEntryConditions().isEmpty(), s.getId());
        }

        StrategyDefinition yang = catalog.find("one_yang_three_yin").orElseThrow();
        assertEquals(14, yang.getScoring().getScoreWeight());
        assertEquals(3, yang.getScoring().getConditions().get("yang_covers_yin_count"));

        StrategyDefinition bull = catalog.find("bull_trend").orElseThrow();
        assertEquals("bullish", bull.getScoring().getConditions().get("ma_alignment"));
        assertEquals(5, bull.getScoring().getConditions().get("min_days"));

        StrategyDefinition chan = catalog.find("chan_theory").orElseThrow();
        assertEquals(true, chan.getScoring().getConditions().get("divergence"));
        assertEquals(true, chan.getScoring().getConditions().get("center_break"));

        StrategyDefinition dual = catalog.find("dual_low").orElseThrow();
        assertTrue(dual.hasScreening());
        assertEquals(2, dual.getScreening().getScoringRules().size());

        StrategyDefinition mom = catalog.find("momentum").orElseThrow();
        assertTrue(mom.hasScreening());
        assertFalse(mom.getScreening().getScoringRules().isEmpty());

        StrategyDefinition reversal = catalog.find("short_reversal").orElseThrow();
        assertEquals(0.05, ((Number) reversal.getScoring().getConditions().get("factor_reversal_5_min")).doubleValue(), 0.001);

        StrategyDefinition channel = catalog.find("channel_breakout").orElseThrow();
        assertEquals("atr", channel.getBacktest().getPositionSizing().get("mode"));

        StrategyDefinition ma = catalog.find("ma_golden_cross").orElseThrow();
        assertEquals("atr", ma.getBacktest().getPositionSizing().get("mode"));
        assertTrue(ma.getBacktest().getExitConditions().stream()
                .anyMatch(c -> "atr_stop".equals(String.valueOf(c.get("type")))));
    }

    @Test
    @DisplayName("factor 条件应映射为 factor_*_min/max scoring keys")
    void factorConditionShouldMapToScoringKeys() {
        Map<String, Object> mapped = StrategyYamlMapper.typedConditionToScoringKeys(
                Map.of("type", "factor", "name", "reversal_5", "min", 0.05));
        assertEquals(0.05, ((Number) mapped.get("factor_reversal_5_min")).doubleValue(), 0.001);

        Map<String, Object> both = StrategyYamlMapper.typedConditionToScoringKeys(
                Map.of("type", "factor", "name", "rsi_14", "min", 40, "max", 70));
        assertEquals(40, ((Number) both.get("factor_rsi_14_min")).doubleValue(), 0.001);
        assertEquals(70, ((Number) both.get("factor_rsi_14_max")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("定义文件应全部使用 rules 段（无顶层旧三段）")
    void allDefinitionFilesShouldUseRulesSection() throws Exception {
        String[] files = {
                "strategies/definitions/technical/ma_golden_cross.yaml",
                "strategies/definitions/technical/volume_breakout.yaml",
                "strategies/definitions/technical/bull_trend.yaml",
                "strategies/definitions/technical/shrink_pullback.yaml",
                "strategies/definitions/technical/box_oscillation.yaml",
                "strategies/definitions/technical/wave_theory.yaml",
                "strategies/definitions/technical/chan_theory.yaml",
                "strategies/definitions/technical/bottom_volume.yaml",
                "strategies/definitions/technical/one_yang_three_yin.yaml",
                "strategies/definitions/technical/momentum.yaml",
                "strategies/definitions/technical/short_reversal.yaml",
                "strategies/definitions/technical/channel_breakout.yaml",
                "strategies/definitions/technical/boll_mean_reversion.yaml",
                "strategies/definitions/sentiment/dragon_head.yaml",
                "strategies/definitions/sentiment/emotion_cycle.yaml",
                "strategies/definitions/sentiment/hot_theme.yaml",
                "strategies/definitions/event/event_driven.yaml",
                "strategies/definitions/fundamental/growth_quality.yaml",
                "strategies/definitions/fundamental/expectation_repricing.yaml",
                "strategies/definitions/fundamental/dual_low.yaml",
                "strategies/definitions/fundamental/value_growth.yaml",
                "strategies/definitions/fundamental/dividend.yaml",
                "strategies/definitions/fundamental/multi_factor.yaml"
        };
        for (String path : files) {
            try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(in, path);
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = yaml.readValue(in, Map.class);
                assertTrue(raw.containsKey("rules"), path + " 应含 rules");
                assertFalse(raw.containsKey("backtest"), path + " 不应再有顶层 backtest");
                assertFalse(raw.containsKey("scoring"), path + " 不应再有顶层 scoring");
                assertFalse(raw.containsKey("screening"), path + " 不应再有顶层 screening");
            }
        }
    }
}
