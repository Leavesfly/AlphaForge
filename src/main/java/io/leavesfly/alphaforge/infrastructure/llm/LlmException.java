package io.leavesfly.alphaforge.infrastructure.llm;

/**
 * LLM 调用异常基类（unchecked，不强制调用方 catch）
 *
 * 所有 LLM 相关异常均继承此类，调用方可通过 catch LlmException 统一捕获，
 * 也可针对具体子类型分别处理（如限流时降级、超时时返回缓存等）。
 */
public class LlmException extends RuntimeException {

    /** 触发异常的模型名称（用于日志诊断） */
    private final String model;

    public LlmException(String message) {
        super(message);
        this.model = null;
    }

    public LlmException(String message, String model) {
        super(message);
        this.model = model;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.model = null;
    }

    public LlmException(String message, String model, Throwable cause) {
        super(message, cause);
        this.model = model;
    }

    public String getModel() {
        return model;
    }

    // ===== 子异常类型 =====

    /**
     * 所有 LLM 渠道均不可用
     * 通常表示：API Key 未配置、网络中断、所有渠道均返回错误
     */
    public static class LlmUnavailableException extends LlmException {
        public LlmUnavailableException(String message) {
            super(message);
        }

        public LlmUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * LLM 请求超时
     * 可重试，通常由网络延迟或模型推理时间过长引起
     */
    public static class LlmTimeoutException extends LlmException {
        public LlmTimeoutException(String message, String model) {
            super(message, model);
        }

        public LlmTimeoutException(String message, String model, Throwable cause) {
            super(message, model, cause);
        }
    }

    /**
     * LLM API 返回限流错误（HTTP 429）
     * 可重试（需等待冷却期），通常由超出供应商配额引起
     */
    public static class LlmRateLimitException extends LlmException {
        /** 建议的冷却等待时间（毫秒），从 Retry-After 头解析 */
        private final long retryAfterMillis;

        public LlmRateLimitException(String message, String model, long retryAfterMillis) {
            super(message, model);
            this.retryAfterMillis = retryAfterMillis;
        }

        public long getRetryAfterMillis() {
            return retryAfterMillis;
        }
    }

    /**
     * LLM 响应解析失败
     * 不可重试（重试也不会改变模型输出格式），通常由模型返回非预期格式引起
     */
    public static class LlmParseException extends LlmException {
        public LlmParseException(String message, String model) {
            super(message, model);
        }

        public LlmParseException(String message, String model, Throwable cause) {
            super(message, model, cause);
        }
    }

    /**
     * LLM 认证/授权失败（HTTP 401/403）
     * 不可重试，需检查 API Key 配置
     */
    public static class LlmAuthException extends LlmException {
        public LlmAuthException(String message, String model) {
            super(message, model);
        }
    }
}
