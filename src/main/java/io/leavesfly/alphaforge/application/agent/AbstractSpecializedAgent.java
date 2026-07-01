package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.application.agent.reasoning.StructuredReasoningPromptBuilder;
import io.leavesfly.alphaforge.application.prompt.PromptManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 专业化子 Agent 抽象基类
 *
 * 提供通用的 LLM 调用 + 工具使用逻辑，子类只需提供角色和 System Prompt。
 */
public abstract class AbstractSpecializedAgent implements SubAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final LlmPort llmService;
    protected final LlmToolAdapter toolAdapter;
    protected final ToolRegistry toolRegistry;
    protected final ObjectMapper objectMapper;

    /** 结构化推理链 Prompt 构建器（可选依赖，字段注入） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    protected StructuredReasoningPromptBuilder reasoningPromptBuilder;

    /** Prompt 模板管理器（可选依赖，字段注入） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    protected PromptManager promptManager;

    protected AbstractSpecializedAgent(LlmPort llmService, LlmToolAdapter toolAdapter,
                                       ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.toolAdapter = toolAdapter;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentResult analyze(String stockCode, String stockName, Map<String, Object> context) {
        long start = System.currentTimeMillis();
        try {
            String userPrompt = buildUserPrompt(stockCode, stockName, context);

            // 结构化推理链增强：在 system prompt 中注入推理框架
            String systemPrompt = resolveSystemPrompt();
            if (reasoningPromptBuilder != null) {
                systemPrompt = reasoningPromptBuilder.enhanceSingleDimensionSystemPrompt(
                        systemPrompt, getRoleDescription());
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            // 使用工具调用循环获取数据并分析
            LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, getMaxToolCalls(), null);

            long duration = System.currentTimeMillis() - start;
            log.debug("[{}] {} 分析完成 耗时:{}ms 工具调用:{}次",
                    stockCode, getName(), duration, result.getTotalToolCalls());

            return parseResult(result.getResponse(), duration);

        } catch (Exception e) {
            log.error("[{}] {} 分析失败: {}", stockCode, getName(), e.getMessage());
            return AgentResult.empty(getName(), getRole());
        }
    }

    /** 构建用户消息（子类可覆盖以定制上下文格式） */
    protected String buildUserPrompt(String stockCode, String stockName, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("请从%s维度分析股票 %s(%s)。\n", getRoleDescription(), stockName, stockCode));

        // 注入上下文数据
        if (context != null && !context.isEmpty()) {
            sb.append("\n已有上下文数据：\n");
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                Object val = entry.getValue();
                if (val != null) {
                    String str = val.toString();
                    if (str.length() > 500) str = str.substring(0, 500) + "...";
                    sb.append("- ").append(entry.getKey()).append(": ").append(str).append("\n");
                }
            }
        }

        sb.append("\n请调用工具获取所需数据，然后给出");
        sb.append(getRoleDescription());
        sb.append("维度的专业分析。\n");
        sb.append("分析结论必须以 JSON 格式返回：\n");
        sb.append("{\"analysis\":\"详细分析\",\"signal\":\"信号(可为null)\",\"score\":0-100,");
        sb.append("\"confidence\":\"高/中等/低\",\"key_findings\":[\"发现1\",\"发现2\"]}");

        return sb.toString();
    }

    /** 从 LLM 响应中解析结果 */
    protected AgentResult parseResult(String response, long durationMs) {
        if (response == null || response.isEmpty()) {
            return AgentResult.empty(getName(), getRole());
        }

        // 尝试从 JSON 中提取
        String analysis = response;
        String signal = null;
        Integer score = null;
        String confidence = "中等";
        List<String> findings = new ArrayList<>();

        try {
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = response.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);

                String a = node.path("analysis").asText("");
                if (!a.isEmpty()) analysis = a;

                String s = node.path("signal").asText("");
                if (!s.isEmpty() && !s.equals("null")) signal = s;

                int sc = node.path("score").asInt(-1);
                if (sc >= 0 && sc <= 100) score = sc;

                String c = node.path("confidence").asText("");
                if (!c.isEmpty()) confidence = c;

                com.fasterxml.jackson.databind.JsonNode findingsNode = node.path("key_findings");
                if (findingsNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode f : findingsNode) {
                        findings.add(f.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JSON 解析失败，使用文本响应: {}", e.getMessage());
        }

        return new AgentResult(getName(), getRole(), analysis, signal, score, confidence,
                findings, durationMs);
    }

    /** 最大工具调用次数（子类可覆盖） */
    protected int getMaxToolCalls() {
        return 3;
    }

    /** 角色描述（如 "技术面" / "基本面" / "风控"） */
    protected abstract String getRoleDescription();

    /**
     * 解析系统 Prompt — 优先使用 PromptManager 外部化模板，fallback 到子类硬编码
     */
    private String resolveSystemPrompt() {
        if (promptManager == null) return getSystemPrompt();
        return promptManager.getTemplateOrDefault(getName() + "_system", getSystemPrompt());
    }
}
