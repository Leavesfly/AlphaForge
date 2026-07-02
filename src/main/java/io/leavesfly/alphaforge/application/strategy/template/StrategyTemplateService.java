package io.leavesfly.alphaforge.application.strategy.template;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略模板服务：提供常见策略类型的预置模板，帮助用户快速创建策略。
 *
 * 模板覆盖四维分类中的典型策略类型，用户可基于模板修改参数和条件。
 */
@Component
public class StrategyTemplateService {

    private final Map<String, StrategyTemplate> templates = new LinkedHashMap<>();

    public StrategyTemplateService() {
        registerTemplates();
    }

    public List<StrategyTemplate> listAll() {
        return List.copyOf(templates.values());
    }

    public StrategyTemplate findById(String templateId) {
        return templates.get(templateId);
    }

    /**
     * 从模板生成策略 YAML，替换占位符为用户指定的策略 ID
     */
    public String generateFromTemplate(String templateId, String newStrategyId, String newLabel) {
        StrategyTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        String yaml = template.getYamlContent()
                .replace("{{STRATEGY_ID}}", newStrategyId)
                .replace("{{STRATEGY_LABEL}}", newLabel != null ? newLabel : template.getLabel());
        return yaml;
    }

    private void registerTemplates() {
        // 技术面模板
        templates.put("tpl_ma_cross", new StrategyTemplate(
                "tpl_ma_cross", "均线交叉策略模板",
                "基于短期均线上穿/下穿长期均线生成买卖信号的经典策略",
                "technical",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 基于均线交叉的策略
                category: technical
                risk_level: medium
                applicable_market: [bull, bear, range]
                applicable_cap: [large, mid, small]

                backtest:
                  parameters:
                    fast_period: 5
                    slow_period: 20
                  param_space:
                    fast_period: [3, 5, 8, 10]
                    slow_period: [15, 20, 30, 60]
                  entry_conditions:
                    - type: ma_cross
                      fast: MA5
                      slow: MA20
                      direction: golden
                  exit_conditions:
                    - type: ma_cross
                      fast: MA5
                      slow: MA20
                      direction: death
                    - type: stop_loss
                      pct: -8
                  position_size: 0.95
                """
        ));

        templates.put("tpl_volume_breakout", new StrategyTemplate(
                "tpl_volume_breakout", "放量突破策略模板",
                "成交量放大配合价格突破的动量策略",
                "technical",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 放量突破策略
                category: technical
                risk_level: medium
                applicable_market: [bull, recovery]

                backtest:
                  parameters:
                    ma_period: 20
                    volume_multiple: 2.0
                    min_change_pct: 3.0
                  entry_conditions:
                    - type: volume_breakout
                      multiple: 2.0
                      min_change: 3.0
                    - type: price_above_ma
                      ma: 20
                  exit_conditions:
                    - type: stop_loss
                      pct: -7
                    - type: take_profit
                      pct: 15
                  position_size: 0.9
                """
        ));

        templates.put("tpl_trend_following", new StrategyTemplate(
                "tpl_trend_following", "趋势跟踪策略模板",
                "价格站稳长期均线且均线多头排列时入场",
                "technical",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 趋势跟踪策略
                category: technical
                risk_level: medium
                applicable_market: [bull]
                tags: [趋势, 均线, 中线]

                backtest:
                  parameters:
                    short_ma: 10
                    long_ma: 30
                    trend_ma: 60
                  entry_conditions:
                    - type: ma_arrangement
                      direction: bullish
                    - type: trend_above
                      ma: 60
                  exit_conditions:
                    - type: break_below_ma
                      ma: 30
                    - type: stop_loss
                      pct: -10
                  position_size: 0.95
                """
        ));

        // 基本面模板
        templates.put("tpl_value_growth", new StrategyTemplate(
                "tpl_value_growth", "价值成长策略模板",
                "基于 ROE、营收增速、PE 的基本面选股策略",
                "fundamental",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 价值成长选股策略
                category: fundamental
                risk_level: low
                applicable_market: [bull, bear, range]
                applicable_cap: [large, mid]

                screening:
                  parameters:
                    min_revenue_growth: 15
                    min_roe: 12
                    max_pe: 30
                  scoring_rules:
                    - type: revenue_growth_min
                      min: 15
                      weight: 30
                    - type: roe_min
                      min: 12
                      weight: 30
                    - type: max_pe
                      max: 30
                      weight: 20
                    - type: profit_growth_min
                      min: 10
                      weight: 20
                  reason_templates:
                    high: 基本面优秀，营收增速{revenue_growth}%，ROE {roe}%，PE {pe}
                    low: 基本面不达标
                """
        ));

        // 情绪面模板
        templates.put("tpl_sentiment_rsi", new StrategyTemplate(
                "tpl_sentiment_rsi", "RSI 情绪极值策略模板",
                "基于 RSI 超买超卖判断情绪极值，逆向操作",
                "sentiment",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: RSI 超买超卖策略
                category: sentiment
                risk_level: high
                applicable_market: [range, bear]

                backtest:
                  parameters:
                    sentiment_period: 14
                    oversold_threshold: 20
                    overbought_threshold: 80
                  entry_conditions:
                    - type: sentiment_extreme
                      level: oversold
                  exit_conditions:
                    - type: sentiment_extreme
                      level: overbought
                    - type: stop_loss
                      pct: -5
                    - type: holding_days
                      max: 10
                  position_size: 0.8
                """
        ));

        // 事件驱动模板
        templates.put("tpl_event_driven", new StrategyTemplate(
                "tpl_event_driven", "事件驱动策略模板",
                "基于当日大幅涨跌模拟事件触发",
                "event",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 事件驱动策略
                category: event
                risk_level: high
                applicable_market: [bull, bear, recovery]

                backtest:
                  parameters:
                    positive_change_threshold: 5.0
                    negative_change_threshold: -5.0
                  entry_conditions:
                    - type: event_trigger
                      category: positive
                      min_change: 5.0
                  exit_conditions:
                    - type: event_trigger
                      category: negative
                      max_change: -5.0
                    - type: stop_loss
                      pct: -8
                    - type: holding_days
                      max: 5
                  position_size: 0.85
                """
        ));

        // 空白模板
        templates.put("tpl_blank", new StrategyTemplate(
                "tpl_blank", "空白策略模板",
                "从零开始定义策略，仅包含基本骨架",
                "technical",
                """
                schema_version: 1
                id: {{STRATEGY_ID}}
                label: {{STRATEGY_LABEL}}
                description: 自定义策略
                category: technical
                risk_level: medium
                applicable_market: [all]
                applicable_cap: [all]

                backtest:
                  parameters: {}
                  entry_conditions: []
                  exit_conditions:
                    - type: stop_loss
                      pct: -8
                  position_size: 0.95
                """
        ));
    }
}
