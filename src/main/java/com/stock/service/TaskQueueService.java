package com.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

/**
 * 异步任务队列服务
 * 对应Python版本的 src/services/task_queue.py + task_service.py
 * 管理后台异步分析任务
 */
@Service
public class TaskQueueService {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    /**
     * 提交异步任务
     */
    public String submitTask(String taskType, Map<String, Object> params, Runnable runnable) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        TaskInfo info = new TaskInfo(taskId, taskType, params);
        tasks.put(taskId, info);

        executor.submit(() -> {
            info.status = "running";
            info.startTime = System.currentTimeMillis();
            try {
                runnable.run();
                info.status = "completed";
            } catch (Exception e) {
                info.status = "failed";
                info.error = e.getMessage();
                log.error("任务执行失败 [{}]: {}", taskId, e.getMessage());
            } finally {
                info.endTime = System.currentTimeMillis();
            }
        });

        log.info("任务已提交: [{}] type={}", taskId, taskType);
        return taskId;
    }

    /** 获取任务状态 */
    public TaskInfo getTask(String taskId) { return tasks.get(taskId); }

    /** 获取所有任务 */
    public List<TaskInfo> getAllTasks() { return new ArrayList<>(tasks.values()); }

    /** 获取运行中的任务 */
    public List<TaskInfo> getRunningTasks() {
        return tasks.values().stream()
                .filter(t -> "running".equals(t.status))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 清理已完成任务 */
    public int cleanCompletedTasks() {
        int before = tasks.size();
        tasks.entrySet().removeIf(e -> "completed".equals(e.getValue().status) || "failed".equals(e.getValue().status));
        return before - tasks.size();
    }

    /** 任务信息 */
    public static class TaskInfo {
        public final String taskId;
        public final String taskType;
        public final Map<String, Object> params;
        public String status = "pending";
        public long startTime;
        public long endTime;
        public String error;
        public Object result;

        public TaskInfo(String taskId, String taskType, Map<String, Object> params) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.params = params;
        }

        public long getDuration() { return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime; }
    }
}
