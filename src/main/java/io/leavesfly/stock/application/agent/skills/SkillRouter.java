package io.leavesfly.stock.application.agent.skills;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent技能路由器
 *
 * 根据用户意图自动路由到合适的技能Agent
 */
@Component
public class SkillRouter {

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public SkillRouter() {
        registerDefaultSkills();
    }

    /**
     * 注册默认技能集
     */
    private void registerDefaultSkills() {
        register("stock_analysis", "股票分析", "分析指定股票的技术面和基本面", List.of("分析", "看看", "怎么样", "走势"));
        register("market_overview", "大盘总览", "分析市场整体走势和情绪", List.of("大盘", "市场", "行情", "指数"));
        register("backtest", "策略回测", "使用历史数据验证交易策略", List.of("回测", "策略", "验证", "测试"));
        register("portfolio", "投资组合", "管理和分析投资组合", List.of("持仓", "组合", "盈亏", "仓位"));
        register("intelligence", "智能情报", "收集和分析市场情报和新闻", List.of("新闻", "情报", "消息", "资讯"));
        register("alert", "告警管理", "设置和管理价格告警", List.of("告警", "提醒", "监控", "通知"));
        register("chat", "自由对话", "回答投资相关的各类问题", List.of());
    }

    /**
     * 路由用户请求到合适的技能
     *
     * @param userInput 用户输入
     * @return 匹配的技能名称
     */
    public String route(String userInput) {
        if (userInput == null || userInput.isEmpty()) return "chat";

        String lower = userInput.toLowerCase();
        int maxScore = 0;
        String bestSkill = "chat";

        for (Map.Entry<String, SkillDefinition> entry : skills.entrySet()) {
            int score = 0;
            for (String trigger : entry.getValue().triggers) {
                if (lower.contains(trigger)) score += 2;
            }
            if (score > maxScore) {
                maxScore = score;
                bestSkill = entry.getKey();
            }
        }
        return bestSkill;
    }

    /**
     * 获取所有已注册技能
     */
    public List<Map<String, Object>> listSkills() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, SkillDefinition> entry : skills.entrySet()) {
            list.add(Map.of(
                    "name", entry.getKey(),
                    "label", entry.getValue().label,
                    "description", entry.getValue().description,
                    "triggers", entry.getValue().triggers
            ));
        }
        return list;
    }

    public void register(String name, String label, String description, List<String> triggers) {
        skills.put(name, new SkillDefinition(label, description, triggers));
    }

    private static class SkillDefinition {
        final String label;
        final String description;
        final List<String> triggers;
        SkillDefinition(String label, String description, List<String> triggers) {
            this.label = label; this.description = description; this.triggers = triggers;
        }
    }
}
