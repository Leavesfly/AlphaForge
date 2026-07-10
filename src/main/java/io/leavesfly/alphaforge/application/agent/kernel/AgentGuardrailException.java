package io.leavesfly.alphaforge.application.agent.kernel;

/**
 * Guardrail 拦截异常 — 当状态变更未获授权或触发风控时抛出。
 */
public class AgentGuardrailException extends RuntimeException {

    public AgentGuardrailException(String message) {
        super(message);
    }

    public AgentGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
