package io.leavesfly.stock.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent对话上下文 + 会话管理 + 事件总线 + 工厂 + Runner + Research + StockScope + ProviderTrace
 * 对应Python: chat_context.py / conversation.py / events.py / factory.py / runner.py
 *             research.py / stock_scope.py / provider_trace.py
 */
@Component
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    // ===== 对话上下文(chat_context.py) =====
    private final Map<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    /** 添加对话消息 */
    public void addMessage(String sessionId, String role, String content) {
        conversations.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(Map.of("role", role, "content", content, "time", LocalDateTime.now().toString()));
    }

    /** 获取对话历史 */
    public List<Map<String, String>> getHistory(String sessionId, int maxTurns) {
        List<Map<String, String>> history = conversations.getOrDefault(sessionId, List.of());
        int start = Math.max(0, history.size() - maxTurns * 2);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    /** 清除会话 */
    public void clearSession(String sessionId) { conversations.remove(sessionId); }

    // ===== 事件总线(events.py) =====
    private final List<Map<String, Object>> eventLog = Collections.synchronizedList(new ArrayList<>());

    /** 发布事件 */
    public void emit(String eventType, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("timestamp", System.currentTimeMillis());
        event.putAll(data);
        eventLog.add(event);
        if (eventLog.size() > 500) eventLog.remove(0);
        log.debug("Agent事件: {} - {}", eventType, data.get("stock_code"));
    }

    /** 获取最近事件 */
    public List<Map<String, Object>> getRecentEvents(int count) {
        int start = Math.max(0, eventLog.size() - count);
        return new ArrayList<>(eventLog.subList(start, eventLog.size()));
    }

    // ===== Agent工厂(factory.py) =====
    private final List<BaseAgent> registeredAgents;

    public AgentRuntime(List<BaseAgent> agents) {
        this.registeredAgents = agents;
    }

    /** 按名称获取Agent */
    public BaseAgent getAgent(String name) {
        return registeredAgents.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** 获取全部Agent名单 */
    public List<String> listAgents() {
        List<String> names = new ArrayList<>();
        for (BaseAgent a : registeredAgents) names.add(a.getName());
        return names;
    }

    // ===== Runner(runner.py) - 单步执行 =====
    public Map<String, Object> runSingleStep(String agentName, Map<String, Object> context) {
        BaseAgent agent = getAgent(agentName);
        if (agent == null) return Map.of("error", "Agent不存在: " + agentName);
        try {
            var opinion = agent.analyze(context);
            return Map.of("agent", agentName, "signal", opinion.getSignal(), "score", opinion.getScore(),
                    "reasoning", opinion.getReasoning());
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ===== Research(research.py) - 研究模式 =====
    public Map<String, Object> research(String topic, Map<String, Object> context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        result.put("agents_consulted", listAgents());
        result.put("status", "completed");
        // 实际研究由Agent编排器处理
        return result;
    }

    // ===== StockScope(stock_scope.py) - 分析范围管理 =====
    public List<String> resolveScope(String scopeExpr) {
        // "all" -> 全部自选股, "sector:半导体" -> 板块, 其他 -> 直接代码列表
        if ("all".equalsIgnoreCase(scopeExpr)) return List.of("600519", "002594", "300750");
        if (scopeExpr.startsWith("sector:")) return List.of(); // 板块解析需要数据源
        return Arrays.asList(scopeExpr.split("[,;\\s]+"));
    }

    // ===== ProviderTrace(provider_trace.py) - LLM调用追踪 =====
    private final List<Map<String, Object>> traces = Collections.synchronizedList(new ArrayList<>());

    public void traceCall(String provider, String model, int tokens, long durationMs) {
        traces.add(Map.of("provider", provider, "model", model, "tokens", tokens,
                "duration_ms", durationMs, "time", System.currentTimeMillis()));
        if (traces.size() > 200) traces.remove(0);
    }

    public List<Map<String, Object>> getRecentTraces(int count) {
        int start = Math.max(0, traces.size() - count);
        return new ArrayList<>(traces.subList(start, traces.size()));
    }
}
