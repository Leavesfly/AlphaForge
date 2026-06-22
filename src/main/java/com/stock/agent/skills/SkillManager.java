package com.stock.agent.skills;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Agent技能系统
 * 对应Python: agent/skills/ (aggregator.py / base.py / defaults.py / skill_agent.py)
 */
@Component
public class SkillManager {

    /** 技能定义 */
    public static class Skill {
        public final String name;
        public final String description;
        public final List<String> requiredTools;
        public Skill(String name, String desc, List<String> tools) {
            this.name = name; this.description = desc; this.requiredTools = tools;
        }
    }

    /** 默认技能列表 */
    private final List<Skill> defaultSkills = List.of(
        new Skill("stock_analysis", "股票综合分析", List.of("get_stock_history", "analyze_technical", "search_news")),
        new Skill("market_overview", "大盘概览", List.of("get_market_light", "get_stock_history")),
        new Skill("risk_assessment", "风险评估", List.of("get_stock_history", "calculate_rsi")),
        new Skill("news_research", "新闻研究", List.of("search_news")),
        new Skill("backtest_strategy", "策略回测", List.of("run_backtest", "get_stock_history"))
    );

    /** 获取所有可用技能 */
    public List<Skill> getAvailableSkills() { return defaultSkills; }

    /** 根据用户意图匹配最佳技能 */
    public Skill matchSkill(String userIntent) {
        String lower = userIntent.toLowerCase();
        if (lower.contains("分析") || lower.contains("怎么样")) return defaultSkills.get(0);
        if (lower.contains("大盘") || lower.contains("市场")) return defaultSkills.get(1);
        if (lower.contains("风险") || lower.contains("止损")) return defaultSkills.get(2);
        if (lower.contains("新闻") || lower.contains("消息")) return defaultSkills.get(3);
        if (lower.contains("回测") || lower.contains("策略")) return defaultSkills.get(4);
        return defaultSkills.get(0); // 默认分析
    }

    /** 聚合多个技能结果 */
    public Map<String, Object> aggregateResults(List<Map<String, Object>> skillResults) {
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("skill_count", skillResults.size());
        StringBuilder summary = new StringBuilder();
        for (Map<String, Object> r : skillResults) {
            summary.append(r.getOrDefault("summary", "")).append("\n");
        }
        aggregated.put("combined_summary", summary.toString().trim());
        return aggregated;
    }
}
