package io.leavesfly.alphaforge.application.agent.kernel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行结果 — 内核对外的统一返回。
 *
 * <p>{@code output} 为主文本回复；{@code data} 承载结构化产物（如 AnalysisResult）；
 * {@code toolCallLog} 记录工具调用轨迹。
 */
public class AgentResult {

    private final boolean success;
    private final AgentTaskType taskType;
    private final String output;
    private final Map<String, Object> data;
    private final List<String> toolCallLog;
    private final int toolCalls;
    private final long durationMs;
    private final String error;

    private AgentResult(Builder b) {
        this.success = b.success;
        this.taskType = b.taskType;
        this.output = b.output;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(b.data));
        this.toolCallLog = b.toolCallLog != null
                ? Collections.unmodifiableList(b.toolCallLog) : Collections.emptyList();
        this.toolCalls = b.toolCalls;
        this.durationMs = b.durationMs;
        this.error = b.error;
    }

    public boolean isSuccess() {
        return success;
    }

    public AgentTaskType getTaskType() {
        return taskType;
    }

    public String getOutput() {
        return output;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Object data(String key) {
        return data.get(key);
    }

    public List<String> getToolCallLog() {
        return toolCallLog;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public static Builder ok(AgentTaskType type) {
        return new Builder().success(true).taskType(type);
    }

    public static AgentResult fail(AgentTaskType type, String error) {
        return new Builder().success(false).taskType(type).error(error).output(error).build();
    }

    public static class Builder {
        private boolean success = true;
        private AgentTaskType taskType;
        private String output = "";
        private final Map<String, Object> data = new LinkedHashMap<>();
        private List<String> toolCallLog;
        private int toolCalls;
        private long durationMs;
        private String error;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder taskType(AgentTaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder output(String output) {
            this.output = output != null ? output : "";
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder toolCallLog(List<String> toolCallLog) {
            this.toolCallLog = toolCallLog;
            return this;
        }

        public Builder toolCalls(int toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(this);
        }
    }
}
