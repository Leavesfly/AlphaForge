package io.leavesfly.alphaforge.application.agent.kernel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 任务描述 — 认知轨的统一入口载体。
 *
 * <p>包含任务类型、目标（自然语言）、输入参数与执行策略（工具调用上限/超时/是否允许状态变更）。
 * 通过 {@link Builder} 构造，保证不可变。
 */
public class AgentTask {

    private final AgentTaskType type;
    private final String goal;
    private final Map<String, Object> inputs;
    private final ExecutionPolicy policy;

    private AgentTask(Builder builder) {
        this.type = builder.type;
        this.goal = builder.goal;
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.inputs));
        this.policy = builder.policy;
    }

    public AgentTaskType getType() {
        return type;
    }

    public String getGoal() {
        return goal;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Object input(String key) {
        return inputs.get(key);
    }

    public String inputString(String key) {
        Object v = inputs.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    public ExecutionPolicy getPolicy() {
        return policy;
    }

    public static Builder of(AgentTaskType type) {
        return new Builder(type);
    }

    /**
     * 执行策略 — 约束单次任务的资源与授权边界。
     */
    public static class ExecutionPolicy {
        private final int maxToolCalls;
        private final int timeoutSeconds;
        private final boolean allowStateMutation;

        public ExecutionPolicy(int maxToolCalls, int timeoutSeconds, boolean allowStateMutation) {
            this.maxToolCalls = maxToolCalls;
            this.timeoutSeconds = timeoutSeconds;
            this.allowStateMutation = allowStateMutation;
        }

        public static ExecutionPolicy defaults() {
            return new ExecutionPolicy(5, 120, false);
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        /** 是否允许状态变更（下单/晋升/写回参数）。默认 false，安全优先。 */
        public boolean isAllowStateMutation() {
            return allowStateMutation;
        }
    }

    public static class Builder {
        private final AgentTaskType type;
        private String goal = "";
        private final Map<String, Object> inputs = new LinkedHashMap<>();
        private ExecutionPolicy policy = ExecutionPolicy.defaults();

        public Builder(AgentTaskType type) {
            if (type == null) {
                throw new IllegalArgumentException("AgentTask.type 不能为空");
            }
            this.type = type;
        }

        public Builder goal(String goal) {
            this.goal = goal != null ? goal : "";
            return this;
        }

        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        public Builder inputs(Map<String, Object> values) {
            if (values != null) {
                this.inputs.putAll(values);
            }
            return this;
        }

        public Builder policy(ExecutionPolicy policy) {
            if (policy != null) {
                this.policy = policy;
            }
            return this;
        }

        public Builder maxToolCalls(int maxToolCalls) {
            this.policy = new ExecutionPolicy(maxToolCalls,
                    this.policy.getTimeoutSeconds(), this.policy.isAllowStateMutation());
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.policy = new ExecutionPolicy(this.policy.getMaxToolCalls(),
                    timeoutSeconds, this.policy.isAllowStateMutation());
            return this;
        }

        public Builder allowStateMutation(boolean allow) {
            this.policy = new ExecutionPolicy(this.policy.getMaxToolCalls(),
                    this.policy.getTimeoutSeconds(), allow);
            return this;
        }

        public AgentTask build() {
            return new AgentTask(this);
        }
    }
}
