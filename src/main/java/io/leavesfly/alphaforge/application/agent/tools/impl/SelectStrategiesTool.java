package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略编排工具 — 供 LLM 根据市场环境与语义标签选择适配策略。
 *
 * 支持三种筛选维度：
 * - 市场阶段（bull/bear/range/recovery）
 * - 市值类型（large/mid/small）
 * - 关键词（匹配策略 tags / label / description）
 */
@Component
public class SelectStrategiesTool implements Tool {

    private final StrategyCatalog catalog;

    public SelectStrategiesTool(StrategyCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "select_strategies";
    }

    @Override
    public String description() {
        return "根据市场环境（牛市/熊市/震荡）和投资偏好，从策略目录中推荐适配的量化策略";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> marketPhase = new HashMap<>();
        marketPhase.put("type", "string");
        marketPhase.put("description", "当前市场阶段: bull(牛市) / bear(熊市) / range(震荡) / recovery(复苏)");
        marketPhase.put("default", "range");
        properties.put("market_phase", marketPhase);

        Map<String, Object> capType = new HashMap<>();
        capType.put("type", "string");
        capType.put("description", "偏好市值类型: large(大盘) / mid(中盘) / small(小盘)，默认 all");
        properties.put("cap_type", capType);

        Map<String, Object> keywords = new HashMap<>();
        keywords.put("type", "string");
        keywords.put("description", "关键词，如 趋势/突破/龙头/价值/抄底，用于语义匹配策略标签");
        properties.put("keywords", keywords);

        Map<String, Object> capability = new HashMap<>();
        capability.put("type", "string");
        capability.put("description", "所需策略能力: backtest / screening / scoring，默认全部");
        properties.put("capability", capability);

        params.put("properties", properties);
        params.put("required", new String[]{});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String marketPhase = (String) args.getOrDefault("market_phase", "range");
        String capType = (String) args.getOrDefault("cap_type", "all");
        String keywords = (String) args.getOrDefault("keywords", "");
        String capability = (String) args.getOrDefault("capability", "");

        List<StrategyDefinition> candidates = catalog.listAll();
        List<StrategyDefinition> filtered = new ArrayList<>();

        for (StrategyDefinition s : candidates) {
            if (!s.isAvailable()) continue;
            if (!capability.isBlank() && !s.supports(capability)) continue;
            if (!s.isApplicableToMarket(marketPhase)) continue;
            if (!capType.isBlank() && !"all".equalsIgnoreCase(capType) && !s.isApplicableToCap(capType)) continue;
            if (!keywords.isBlank() && !matchesKeywords(s, keywords)) continue;
            filtered.add(s);
        }

        if (filtered.isEmpty()) {
            return String.format("未找到匹配的策略（市场=%s, 市值=%s, 关键词=%s）", marketPhase, capType, keywords);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("推荐 %d 个策略（市场=%s, 市值=%s, 关键词=%s）：\n\n",
                filtered.size(), marketPhase, capType, keywords.isBlank() ? "无" : keywords));
        for (StrategyDefinition s : filtered) {
            sb.append(String.format("- %s（%s）: %s | 风险=%s | 能力=%s | 标签=%s\n",
                    s.getId(), s.getLabel(), s.getDescription(),
                    s.getRiskLevel(), s.getCapabilities(), s.getTags()));
        }
        return sb.toString().trim();
    }

    private boolean matchesKeywords(StrategyDefinition s, String keywords) {
        String lower = keywords.toLowerCase();
        for (String tag : s.getTags()) {
            if (tag.toLowerCase().contains(lower) || lower.contains(tag.toLowerCase())) return true;
        }
        if (s.getLabel().toLowerCase().contains(lower)) return true;
        if (s.getDescription().toLowerCase().contains(lower)) return true;
        return false;
    }
}
