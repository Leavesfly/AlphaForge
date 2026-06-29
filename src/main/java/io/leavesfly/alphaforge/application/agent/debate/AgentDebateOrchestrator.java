package io.leavesfly.alphaforge.application.agent.debate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.agent.MultiAgentOrchestrator;
import io.leavesfly.alphaforge.application.agent.SubAgent;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 辩论编排器 — 在现有多 Agent 架构上增加对抗式验证
 *
 * 对应论文：
 * - ContestTrade: 内部竞争机制，Agent 之间通过辩论选出最优信号
 * - FactorMAD: 多 Agent 辩论框架，正反方对结论进行论证/反驳
 *
 * 辩论三轮制：
 * Round 1 — 独立分析（复用现有 MultiAgentOrchestrator.orchestrate）
 * Round 2 — 交叉质询（各 Agent 看到他人结论后，形成支持/反对论点）
 * Round 3 — 裁判裁决（LLM 综合所有论点给出最终判决）
 *
 * 设计原则（小改动）：
 * - 不修改现有 SubAgent / AbstractSpecializedAgent 代码
 * - 不新增 Agent 类（辩论轮次通过 LLM prompt 实现）
 * - 与现有 MultiAgentOrchestrator 并列，可通过配置切换
 */
@Component
public class AgentDebateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentDebateOrchestrator.class);

    private final LlmPort llmPort;
    private final MultiAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    /** 风控 Agent 角色名（其反对意见有"一票否决权"） */
    private static final String RISK_ROLE = "risk";

    public AgentDebateOrchestrator(LlmPort llmPort, MultiAgentOrchestrator orchestrator) {
        this.llmPort = llmPort;
        this.orchestrator = orchestrator;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行带辩论的多 Agent 分析
     *
     * @param stockCode        股票代码
     * @param stockName        股票名称
     * @param context          共享上下文
     * @param maxTimeoutSeconds 最大超时
     * @return 辩论结果
     */
    public DebateResult orchestrateWithDebate(String stockCode, String stockName,
                                                Map<String, Object> context, int maxTimeoutSeconds) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Agent 辩论模式启动", stockCode);

        // ===== Round 1: 独立分析（复用现有编排器） =====
        MultiAgentOrchestrator.OrchestrationResult round1 = orchestrator.orchestrate(
                stockCode, stockName, context, maxTimeoutSeconds);

        List<SubAgent.AgentResult> initialResults = round1.agentResults();
        String originalSynthesis = round1.synthesis();

        log.info("[{}] Round 1 完成，{} 个 Agent 给出初步结论", stockCode, initialResults.size());

        // 提取原始信号和评分（用于后续对比）
        String originalSignal = extractSignal(originalSynthesis);
        int originalScore = extractScore(originalSynthesis);

        // ===== Round 2: 交叉质询 =====
        List<DebateArgument> arguments = crossExamine(stockCode, stockName, initialResults, originalSynthesis);
        DebateRound round2 = new DebateRound(2, DebateRound.RoundType.CROSS_EXAMINATION,
                arguments, buildArgumentsSummary(arguments));

        log.info("[{}] Round 2 完成，{} 个论点（支持={}, 反对={}, 中立={}）",
                stockCode, arguments.size(),
                round2.countByStance(DebateArgument.Stance.SUPPORT),
                round2.countByStance(DebateArgument.Stance.OPPOSE),
                round2.countByStance(DebateArgument.Stance.NEUTRAL));

        // ===== Round 3: 裁判裁决 =====
        DebateVerdict verdict = judge(stockCode, stockName,
                initialResults, arguments, originalSynthesis, originalSignal, originalScore);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[{}] Agent 辩论完成 | 原始信号:{} → 最终信号:{} 评分:{}→{} 耗时:{}ms",
                stockCode, originalSignal, verdict.getFinalSignal(),
                originalScore, verdict.getFinalScore(), duration);

        return new DebateResult(initialResults, round2, verdict, originalSynthesis, duration, true);
    }

    // ===== Round 2: 交叉质询 =====

    /**
     * 交叉质询 — 让每个 Agent 看到其他 Agent 的结论后形成论点
     *
     * 实现方式：用一次 LLM 调用，将所有 Agent 结论 + 原始综合结论
     * 作为 prompt 输入，让 LLM 模拟每个 Agent 的视角进行辩论。
     */
    private List<DebateArgument> crossExamine(String stockCode, String stockName,
                                                List<SubAgent.AgentResult> results,
                                                String originalSynthesis) {
        String systemPrompt = """
                你是一位多 Agent 辩论协调员。以下是多个专业 Agent 对同一只股票的独立分析结论。
                请让每个 Agent 看到其他 Agent 的结论后，从自己的专业角度进行交叉质询。

                每个 Agent 需要表明立场：
                - SUPPORT: 支持当前综合结论，说明为什么同意
                - OPPOSE: 反对当前综合结论，指出其他 Agent 的分析漏洞
                - NEUTRAL: 补充其他 Agent 遗漏的视角

                风控 Agent 的反对意见应被特别重视。

                返回 JSON 格式：
                {"arguments": [
                    {"agent_name":"...", "role":"...", "stance":"SUPPORT/OPPOSE/NEUTRAL",
                     "argument":"论点内容", "target_agent":"针对的Agent名(null表示针对整体)",
                     "evidence":["证据1","证据2"]}
                ]}
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append(String.format("股票: %s(%s)\n\n", stockName, stockCode));
        userPrompt.append("## 各 Agent 独立分析结论\n\n");

        for (SubAgent.AgentResult r : results) {
            userPrompt.append(String.format("### %s (角色: %s, 置信度: %s)\n",
                    r.agentName, r.role, r.confidence));
            if (r.signal != null) userPrompt.append("信号: ").append(r.signal).append("\n");
            if (r.score != null) userPrompt.append("评分: ").append(r.score).append("\n");
            userPrompt.append("分析: ").append(truncate(r.analysis, 500)).append("\n");
            if (!r.keyFindings.isEmpty()) {
                userPrompt.append("关键发现:\n");
                for (String f : r.keyFindings) userPrompt.append("- ").append(f).append("\n");
            }
            userPrompt.append("\n");
        }

        userPrompt.append("## 原始综合结论\n").append(truncate(originalSynthesis, 500)).append("\n\n");
        userPrompt.append("请让每个 Agent 进行交叉质询，返回 JSON。");

        try {
            String response = llmPort.chat(systemPrompt, userPrompt.toString());
            return parseArguments(response, results);
        } catch (Exception e) {
            log.warn("[{}] 交叉质询失败，跳过辩论: {}", stockCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DebateArgument> parseArguments(String response, List<SubAgent.AgentResult> originalResults) {
        List<DebateArgument> arguments = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode argsArray = root.path("arguments");

            if (!argsArray.isArray()) return arguments;

            // 创建 Agent 名称 → 原始结果 的映射
            Map<String, SubAgent.AgentResult> resultMap = new HashMap<>();
            for (SubAgent.AgentResult r : originalResults) {
                resultMap.put(r.agentName, r);
            }

            for (JsonNode argNode : argsArray) {
                String agentName = argNode.path("agent_name").asText("");
                String role = argNode.path("role").asText("");
                String stanceStr = argNode.path("stance").asText("NEUTRAL");
                String argument = argNode.path("argument").asText("");
                String targetAgent = argNode.path("target_agent").asText(null);
                if ("null".equals(targetAgent) || targetAgent == null || targetAgent.isBlank()) {
                    targetAgent = null;
                }

                List<String> evidence = new ArrayList<>();
                JsonNode evidenceNode = argNode.path("evidence");
                if (evidenceNode.isArray()) {
                    for (JsonNode e : evidenceNode) evidence.add(e.asText());
                }

                // 从原始结果中获取评分和信号
                SubAgent.AgentResult original = resultMap.get(agentName);
                Integer originalScore = original != null ? original.score : null;
                String originalSignal = original != null ? original.signal : null;

                DebateArgument.Stance stance;
                try {
                    stance = DebateArgument.Stance.valueOf(stanceStr.toUpperCase());
                } catch (Exception e) {
                    stance = DebateArgument.Stance.NEUTRAL;
                }

                arguments.add(new DebateArgument(agentName, role, stance, argument,
                        targetAgent, evidence, originalScore, originalSignal));
            }
        } catch (Exception e) {
            log.warn("解析辩论论点失败: {}", e.getMessage());
        }
        return arguments;
    }

    // ===== Round 3: 裁判裁决 =====

    /**
     * 裁判裁决 — LLM 综合所有论点给出最终判决
     */
    private DebateVerdict judge(String stockCode, String stockName,
                                  List<SubAgent.AgentResult> initialResults,
                                  List<DebateArgument> arguments,
                                  String originalSynthesis,
                                  String originalSignal, int originalScore) {
        String systemPrompt = """
                你是一位投资决策裁判，负责综合多 Agent 辩论结果做出最终判决。

                裁判规则：
                1. 评估各方论点的逻辑强度和数据支撑，不简单"少数服从多数"
                2. 风控 Agent 的反对意见有"一票否决权"：如果风控 Agent 明确反对，应降级信号
                3. 当多方分歧巨大时，自动降低置信度
                4. 评分可基于辩论结果调整（±20分以内）

                返回 JSON 格式：
                {"final_signal":"strong_buy/buy/neutral/sell/strong_sell",
                 "final_score":0-100,
                 "confidence":"高/中等/低",
                 "reasoning":"裁判推理过程",
                 "key_conclusions":["结论1","结论2"],
                 "risk_note":"风险提示",
                 "operation_advice":"操作建议",
                 "adopted_agents":["采纳的Agent名"],
                 "rejected_agents":["否决的Agent名"],
                 "consensus_level":0.0-1.0}
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append(String.format("股票: %s(%s)\n\n", stockName, stockCode));

        // 原始综合结论
        userPrompt.append("## 原始综合结论（无辩论时）\n");
        userPrompt.append(String.format("信号: %s 评分: %d\n\n", originalSignal, originalScore));

        // 辩论论点
        userPrompt.append("## 交叉质询论点\n\n");
        for (DebateArgument arg : arguments) {
            userPrompt.append(String.format("### %s (%s) — 立场: %s\n",
                    arg.getAgentName(), arg.getRole(), arg.getStance()));
            if (arg.getTargetAgent() != null) {
                userPrompt.append("针对: ").append(arg.getTargetAgent()).append("\n");
            }
            userPrompt.append("论点: ").append(arg.getArgument()).append("\n");
            if (!arg.getEvidence().isEmpty()) {
                userPrompt.append("证据:\n");
                for (String e : arg.getEvidence()) userPrompt.append("- ").append(e).append("\n");
            }
            userPrompt.append("\n");
        }

        // 统计
        long support = arguments.stream().filter(a -> a.getStance() == DebateArgument.Stance.SUPPORT).count();
        long oppose = arguments.stream().filter(a -> a.getStance() == DebateArgument.Stance.OPPOSE).count();
        userPrompt.append(String.format("## 辩论统计\n支持: %d  反对: %d  中立: %d\n\n",
                support, oppose, arguments.size() - support - oppose));

        // 风控 Agent 是否反对
        boolean riskOpposes = arguments.stream()
                .anyMatch(a -> RISK_ROLE.equals(a.getRole()) && a.getStance() == DebateArgument.Stance.OPPOSE);
        if (riskOpposes) {
            userPrompt.append("⚠ 风控 Agent 明确反对，请慎重评估。\n\n");
        }

        userPrompt.append("请做出最终判决，返回 JSON。");

        try {
            String response = llmPort.chat(systemPrompt, userPrompt.toString());
            return parseVerdict(response, originalSignal, originalScore);
        } catch (Exception e) {
            log.warn("[{}] 裁判裁决失败，使用原始结论: {}", stockCode, e.getMessage());
            return new DebateVerdict(originalSignal, originalScore, "低",
                    originalSynthesis, List.of(), "", "", List.of(), List.of(),
                    0.5, originalScore, originalSignal);
        }
    }

    private DebateVerdict parseVerdict(String response, String originalSignal, int originalScore) {
        try {
            JsonNode root = objectMapper.readTree(response);

            String finalSignal = root.path("final_signal").asText(originalSignal);
            int finalScore = root.path("final_score").asInt(originalScore);
            String confidence = root.path("confidence").asText("中等");
            String reasoning = root.path("reasoning").asText("");
            String riskNote = root.path("risk_note").asText("");
            String advice = root.path("operation_advice").asText("");
            double consensus = root.path("consensus_level").asDouble(0.5);

            List<String> conclusions = new ArrayList<>();
            JsonNode conclusionsNode = root.path("key_conclusions");
            if (conclusionsNode.isArray()) {
                for (JsonNode c : conclusionsNode) conclusions.add(c.asText());
            }

            List<String> adopted = new ArrayList<>();
            JsonNode adoptedNode = root.path("adopted_agents");
            if (adoptedNode.isArray()) {
                for (JsonNode a : adoptedNode) adopted.add(a.asText());
            }

            List<String> rejected = new ArrayList<>();
            JsonNode rejectedNode = root.path("rejected_agents");
            if (rejectedNode.isArray()) {
                for (JsonNode r : rejectedNode) rejected.add(r.asText());
            }

            return new DebateVerdict(finalSignal, finalScore, confidence, reasoning,
                    conclusions, riskNote, advice, adopted, rejected,
                    consensus, originalScore, originalSignal);
        } catch (Exception e) {
            log.warn("解析裁判裁决失败: {}", e.getMessage());
            return new DebateVerdict(originalSignal, originalScore, "低",
                    response, List.of(), "", "", List.of(), List.of(),
                    0.5, originalScore, originalSignal);
        }
    }

    // ===== 辅助方法 =====

    private String extractSignal(String synthesis) {
        if (synthesis == null) return "neutral";
        try {
            int idx = synthesis.indexOf("\"signal\"");
            if (idx >= 0) {
                int colon = synthesis.indexOf(":", idx);
                int q1 = synthesis.indexOf("\"", colon + 1);
                int q2 = synthesis.indexOf("\"", q1 + 1);
                if (q1 >= 0 && q2 > q1) return synthesis.substring(q1 + 1, q2);
            }
        } catch (Exception ignored) {}
        return "neutral";
    }

    private int extractScore(String synthesis) {
        if (synthesis == null) return 50;
        try {
            int idx = synthesis.indexOf("\"score\"");
            if (idx >= 0) {
                int colon = synthesis.indexOf(":", idx);
                int start = colon + 1;
                while (start < synthesis.length() && !Character.isDigit(synthesis.charAt(start))) start++;
                int end = start;
                while (end < synthesis.length() && (Character.isDigit(synthesis.charAt(end)) || synthesis.charAt(end) == '.')) end++;
                return (int) Double.parseDouble(synthesis.substring(start, end));
            }
        } catch (Exception ignored) {}
        return 50;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String buildArgumentsSummary(List<DebateArgument> arguments) {
        if (arguments.isEmpty()) return "无辩论论点";
        StringBuilder sb = new StringBuilder();
        for (DebateArgument a : arguments) {
            sb.append(String.format("- %s(%s): %s — %s\n",
                    a.getAgentName(), a.getStance(),
                    truncate(a.getArgument(), 100),
                    a.getTargetAgent() != null ? "→ " + a.getTargetAgent() : ""));
        }
        return sb.toString();
    }
}
