package io.leavesfly.alphaforge.infrastructure.llm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * LLM 监控指标埋点 — Micrometer 指标定义与记录
 *
 * 指标列表：
 * - llm.call.duration (Timer): LLM 调用耗时，tag: model, method
 * - llm.call.total (Counter): LLM 调用次数，tag: model, method, status(success/fail)
 * - llm.tokens.consumed (Counter): Token 消耗量，tag: model, type(prompt/completion)
 *
 * 当 MeterRegistry 不可用时（未引入 actuator），所有方法为空操作。
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
public class LlmMetrics {

    private static final Logger log = LoggerFactory.getLogger(LlmMetrics.class);

    private final MeterRegistry meterRegistry;

    public LlmMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("LlmMetrics 初始化完成，Micrometer 指标已启用");
    }

    /** 记录 LLM 调用耗时 */
    public void recordCallDuration(String model, String method, long durationMs) {
        Timer.builder("llm.call.duration")
                .tag("model", model != null ? model : "unknown")
                .tag("method", method)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    /** 记录 LLM 调用结果（成功/失败） */
    public void recordCallResult(String model, String method, boolean success) {
        Counter.builder("llm.call.total")
                .tag("model", model != null ? model : "unknown")
                .tag("method", method)
                .tag("status", success ? "success" : "fail")
                .register(meterRegistry)
                .increment();
    }

    /** 记录 Token 消耗 */
    public void recordTokenUsage(String model, String type, int tokens) {
        if (tokens <= 0) return;
        Counter.builder("llm.tokens.consumed")
                .tag("model", model != null ? model : "unknown")
                .tag("type", type) // prompt / completion
                .register(meterRegistry)
                .increment(tokens);
    }

    /** 记录工具调用次数 */
    public void recordToolCall(String toolName, boolean success, long durationMs) {
        Counter.builder("agent.tool.calls")
                .tag("tool", toolName)
                .tag("status", success ? "success" : "fail")
                .register(meterRegistry)
                .increment();

        Timer.builder("agent.tool.duration")
                .tag("tool", toolName)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }
}
