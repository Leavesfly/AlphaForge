package io.leavesfly.alphaforge.application.agent.reasoning;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 结构化推理链 Prompt 构建器 — 将推理链注入到 LLM 的 system/user prompt 中
 *
 * 核心能力：
 * 1. 为单维度 Agent（SubAgent）构建带推理链的 system prompt 增量
 * 2. 为多维度综合（MultiAgentOrchestrator）构建带分层推理链的综合 prompt
 * 3. 构建 JSON 输出格式约束（要求推理过程可审计）
 * 4. 注入 Few-shot 推理范例（从信号反馈中学习）
 *
 * 设计原则：
 * - 纯 prompt 层改造，不修改现有 Agent 类的执行逻辑
 * - 推理链作为 system prompt 的增强指令注入
 * - 输出格式从简单 JSON 扩展为包含推理过程的结构化 JSON
 */

/**
 * 推理增强模式选择
 */

class ReasoningModeHolder {
    /** 推理模式：flat=扁平推理链, hierarchical=分层推理链 */
    static String mode = "hierarchical";
}

/**
 * 结构化推理链 Prompt 构建器 — 将推理链注入到 LLM 的 system/user prompt 中
 *
 * 核心能力：
 * 1. 为单维度 Agent（SubAgent）构建带推理链的 system prompt 增量
 * 2. 为多维度综合（MultiAgentOrchestrator）构建带推理链的综合 prompt
 * 3. 构建 JSON 输出格式约束（要求推理过程可审计）
 *
 * 设计原则：
 * - 纯 prompt 层改造，不修改现有 Agent 类的执行逻辑
 * - 推理链作为 system prompt 的增强指令注入
 * - 输出格式从简单 JSON 扩展为包含推理过程的结构化 JSON
 */
@Component
public class StructuredReasoningPromptBuilder {

    /** 推理链开关（可通过配置关闭） */
    private volatile boolean enabled = true;

    /** 推理模式：flat=扁平6步, hierarchical=4层分层 */
    private volatile String reasoningMode = "hierarchical";

    /** 可选依赖：Few-shot 推理模板构建器 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FewShotReasoningTemplateBuilder fewShotTemplateBuilder;

    /**
     * 为 SubAgent 构建 system prompt 增量
     *
     * @param originalSystemPrompt 原始 system prompt
     * @param roleDescription      角色描述（如 "技术面"）
     * @return 增强后的 system prompt（如果 disabled 则返回原始 prompt）
     */
    public String enhanceSingleDimensionSystemPrompt(String originalSystemPrompt,
                                                       String roleDescription) {
        if (!enabled) return originalSystemPrompt;

        // 分层推理模式：注入分层推理框架
        if ("hierarchical".equals(reasoningMode)) {
            return enhanceWithHierarchicalReasoning(originalSystemPrompt, roleDescription);
        }

        // 扁平推理模式（原有逻辑）
        ReasoningChain chain = ReasoningChain.forSingleDimension(roleDescription);

        StringBuilder sb = new StringBuilder(originalSystemPrompt);
        sb.append("\n\n## 结构化推理要求\n");
        sb.append("你必须按照以下推理链逐步分析，不得跳步：\n\n");

        for (ReasoningStep step : chain.getSteps()) {
            sb.append(String.format("### %s（%s）\n%s\n\n",
                    step.getName(), step.getGoal(), step.getInstruction()));
        }

        sb.append("## 推理质量要求\n");
        sb.append("- 每步推理必须引用具体数据（指标值、数值、日期）作为支撑\n");
        sb.append("- 「验证」步骤需要至少 2 个独立证据交叉验证\n");
        sb.append("- 「风险」步骤必须提出至少 1 个反面论点\n");
        sb.append("- 如果验证失败（证据矛盾），应降低置信度或修改假设\n\n");

        // 注入 Few-shot 推理范例
        if (fewShotTemplateBuilder != null) {
            sb.append(fewShotTemplateBuilder.buildReasoningQualityChecklist());
        }

        sb.append("## 输出格式（含推理过程）\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"reasoning\": {\n");
        sb.append("    \"observation\": \"客观数据描述\",\n");
        sb.append("    \"assessment\": \"初步判断\",\n");
        sb.append("    \"hypothesis\": \"假设\",\n");
        sb.append("    \"evidence\": [\"证据1\", \"证据2\"],\n");
        sb.append("    \"risk_check\": \"反面论证\"\n");
        sb.append("  },\n");
        sb.append("  \"analysis\": \"综合分析（含推理过程总结）\",\n");
        sb.append("  \"signal\": \"strong_buy/buy/neutral/sell/strong_sell\",\n");
        sb.append("  \"score\": 0-100,\n");
        sb.append("  \"confidence\": \"高/中等/低\",\n");
        sb.append("  \"key_findings\": [\"发现1\", \"发现2\"]\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 分层推理增强 — 注入宏观→行业→个股→综合 4 层推理框架
     */
    private String enhanceWithHierarchicalReasoning(String originalSystemPrompt, String roleDescription) {
        HierarchicalReasoningChain chain = HierarchicalReasoningChain.standard();

        StringBuilder sb = new StringBuilder(originalSystemPrompt);
        sb.append("\n\n## 分层结构化推理要求\n");
        sb.append("你必须按照以下四层推理框架逐步分析，不允许跳层：\n");
        sb.append("（当前角色：").append(roleDescription).append("，请聚焦本层职责）\n\n");

        for (HierarchicalReasoningChain.ReasoningLayer layer : chain.getLayers()) {
            sb.append(String.format("### %s: %s\n%s\n",
                    layer.getLayerId(), layer.getLayerName(), layer.getInstruction()));
        }

        sb.append("## 推理质量要求\n");
        sb.append("- 每层推理必须引用具体数据作为支撑\n");
        sb.append("- 上层结论影响下层权重（如熊市时风控权重↑）\n");
        sb.append("- 个股层需至少 2 个独立因子交叉验证\n");
        sb.append("- 综合层必须检查各层一致性\n\n");

        // 注入 Few-shot 推理范例和自检清单
        if (fewShotTemplateBuilder != null) {
            sb.append(fewShotTemplateBuilder.buildReasoningQualityChecklist());
        }

        sb.append("\n## 输出格式（含分层推理过程）\n");
        sb.append(HierarchicalReasoningChain.buildOutputFormat());

        return sb.toString();
    }

    /**
     * 为多维度综合构建 user prompt 增量
     *
     * @param stockCode      股票代码
     * @param stockName      股票名称
     * @param agentResults   各 Agent 分析结果文本
     * @param marketPhase    当前市场阶段
     * @return 增强后的 user prompt
     */
    public String buildMultiDimensionalUserPrompt(String stockCode, String stockName,
                                                    String agentResults, String marketPhase) {
        if (!enabled) {
            // 不启用推理链时，使用原始格式
            return String.format("""
                    请综合以下各专业 Agent 的分析结论，给出 %s(%s) 的最终分析报告。

                    %s

                    请综合以上各维度分析，给出最终结论。
                    最终结论以 JSON 格式返回。
                    """, stockName, stockCode, agentResults);
        }

        // 分层推理模式
        if ("hierarchical".equals(reasoningMode)) {
            return buildHierarchicalMultiDimensionalPrompt(stockCode, stockName, agentResults, marketPhase);
        }

        // 扁平推理模式（原有逻辑）
        ReasoningChain chain = ReasoningChain.forMultiDimensional();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请按照结构化推理链综合以下各专业 Agent 的分析结论，给出 %s(%s) 的最终分析报告。\n\n",
                stockName, stockCode));

        sb.append("当前市场阶段：").append(marketPhase != null ? marketPhase : "未知").append("\n\n");

        sb.append("## 各 Agent 分析结果\n\n");
        sb.append(agentResults);
        sb.append("\n");

        // 注入 Few-shot 推理范例
        if (fewShotTemplateBuilder != null) {
            String fewShot = fewShotTemplateBuilder.buildFewShotTemplate(stockCode);
            if (fewShot != null && !fewShot.isBlank()) {
                sb.append(fewShot);
            }
        }

        sb.append("## 推理链要求\n");
        sb.append("请严格按照以下步骤逐步推理：\n\n");

        for (ReasoningStep step : chain.getSteps()) {
            sb.append(String.format("### %s — %s\n%s\n\n",
                    step.getName(), step.getGoal(), step.getInstruction()));
        }

        sb.append("## 最终输出格式（含推理过程）\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"reasoning\": {\n");
        sb.append("    \"summary\": \"各维度结论汇总\",\n");
        sb.append("    \"consistency\": \"一致性分析\",\n");
        sb.append("    \"weighting\": \"权重分配理由\",\n");
        sb.append("    \"synthesis\": \"综合判断过程\",\n");
        sb.append("    \"risk_review\": \"风险审查\"\n");
        sb.append("  },\n");
        sb.append("  \"signal\": \"strong_buy/buy/neutral/sell/strong_sell\",\n");
        sb.append("  \"score\": 0-100,\n");
        sb.append("  \"confidence\": \"高/中等/低\",\n");
        sb.append("  \"summary\": \"关键结论（1-3句话）\",\n");
        sb.append("  \"operation_advice\": \"操作建议\",\n");
        sb.append("  \"risk_note\": \"风险提示\"\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 构建 ChatService 对话场景的推理增强 prompt
     *
     * @param userQuestion  用户问题
     * @param stockContext  股票上下文信息
     * @return 增强后的 system prompt 增量
     */
    public String buildChatReasoningEnhancement(String stockContext) {
        if (!enabled) return "";

        return """

               ## 结构化推理要求
               回答股票相关问题时，请按以下步骤推理：
               1. 先确认用户问题涉及的分析维度（技术面/基本面/行业面/情绪面）
               2. 从已知数据中提取相关证据
               3. 形成初步判断
               4. 检查是否有矛盾的证据
               5. 给出结论，并说明置信度和风险

               已知上下文：
               """ + (stockContext != null ? stockContext : "无");
    }

    /**
     * 从 LLM 响应中提取推理过程（如果 LLM 按格式返回了 reasoning 字段）
     */
    public String extractReasoning(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) return "";
        try {
            // 尝试从 JSON 中提取 reasoning 字段
            int reasoningIdx = llmResponse.indexOf("\"reasoning\"");
            if (reasoningIdx < 0) return "";

            // 找到 reasoning 对象的起始和结束
            int objStart = llmResponse.indexOf("{", reasoningIdx);
            if (objStart < 0) return "";

            int depth = 0;
            int objEnd = objStart;
            for (int i = objStart; i < llmResponse.length(); i++) {
                char c = llmResponse.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { objEnd = i; break; }
                }
            }

            return llmResponse.substring(objStart, objEnd + 1);
        } catch (Exception e) {
            return "";
        }
    }

    // ===== 配置 =====

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReasoningMode() { return reasoningMode; }

    public void setReasoningMode(String reasoningMode) {
        this.reasoningMode = reasoningMode;
    }

    // ===== 分层推理模式 =====

    /**
     * 构建分层推理的多维度综合 prompt
     */
    private String buildHierarchicalMultiDimensionalPrompt(String stockCode, String stockName,
                                                             String agentResults, String marketPhase) {
        HierarchicalReasoningChain chain = HierarchicalReasoningChain.standard();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请按照分层推理链综合以下各专业 Agent 的分析结论，给出 %s(%s) 的最终分析报告。\n\n",
                stockName, stockCode));

        sb.append("当前市场阶段：").append(marketPhase != null ? marketPhase : "未知").append("\n\n");

        sb.append("## 各 Agent 分析结果\n\n");
        sb.append(agentResults);
        sb.append("\n");

        // 注入 Few-shot 推理范例（从信号反馈中学习）
        if (fewShotTemplateBuilder != null) {
            String fewShot = fewShotTemplateBuilder.buildFewShotTemplate(stockCode);
            if (fewShot != null && !fewShot.isBlank()) {
                sb.append(fewShot);
            }
        }

        sb.append("## 分层推理要求\n");
        sb.append("请严格按照以下四层推理框架逐步推理，不允许跳层：\n\n");

        for (HierarchicalReasoningChain.ReasoningLayer layer : chain.getLayers()) {
            sb.append(String.format("### %s: %s\n%s\n",
                    layer.getLayerId(), layer.getLayerName(), layer.getInstruction()));
        }

        // 推理质量自检清单
        if (fewShotTemplateBuilder != null) {
            sb.append(fewShotTemplateBuilder.buildReasoningQualityChecklist());
            sb.append("\n");
        }

        sb.append("## 最终输出格式（含分层推理过程）\n");
        sb.append(HierarchicalReasoningChain.buildOutputFormat());

        return sb.toString();
    }
}
