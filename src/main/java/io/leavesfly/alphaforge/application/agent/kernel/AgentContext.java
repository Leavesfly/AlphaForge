package io.leavesfly.alphaforge.application.agent.kernel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 工作上下文（Blackboard）— 贯穿规划-执行-反思的可读写共享工作记忆。
 *
 * <p>替代此前散落在各处的 {@code Map<String,Object> context}，统一承载中间产物、
 * 注入的学习提示（learning_prompt）以及执行轨迹（trace）。
 *
 * <p>线程安全：working 使用同步 Map（容忍 null 值），trace 使用 COW 列表。
 */
public class AgentContext {

    private final String taskId;
    private final AgentTask task;
    private final Map<String, Object> working = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<String> trace = new CopyOnWriteArrayList<>();
    private final long startTime = System.currentTimeMillis();

    public AgentContext(AgentTask task) {
        this.task = task;
        this.taskId = UUID.randomUUID().toString();
        if (task != null && task.getInputs() != null) {
            this.working.putAll(task.getInputs());
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public AgentTask getTask() {
        return task;
    }

    public void put(String key, Object value) {
        working.put(key, value);
    }

    public Object get(String key) {
        return working.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object v = working.get(key);
        return type.isInstance(v) ? (T) v : null;
    }

    public String getString(String key) {
        Object v = working.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    public boolean has(String key) {
        return working.containsKey(key);
    }

    /** 返回工作记忆快照（不可变副本）。 */
    public Map<String, Object> snapshot() {
        synchronized (working) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(working));
        }
    }

    public void trace(String step) {
        if (step != null) {
            trace.add(step);
        }
    }

    public List<String> getTrace() {
        return Collections.unmodifiableList(trace);
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - startTime;
    }
}
