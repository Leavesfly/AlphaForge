package io.leavesfly.stock.infrastructure.persistence.analysis;

import io.leavesfly.stock.domain.model.entity.analysis.AnalysisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析任务数据访问层
 */
@Mapper
public interface AnalysisTaskRepository {

    void insert(AnalysisTask task);

    void update(AnalysisTask task);

    AnalysisTask findByTaskId(@Param("taskId") String taskId);

    List<AnalysisTask> findByStatus(@Param("status") String status);

    List<AnalysisTask> findRecent(@Param("limit") int limit);

    void deleteByTaskId(@Param("taskId") String taskId);

    int count();

    void updateStatus(@Param("taskId") String taskId, @Param("status") String status,
                      @Param("progress") Integer progress, @Param("message") String message);

    void updateCompleted(@Param("taskId") String taskId, @Param("status") String status,
                         @Param("progress") Integer progress, @Param("result") String result,
                         @Param("errorMessage") String errorMessage, @Param("completedAt") LocalDateTime completedAt);

    default AnalysisTask save(AnalysisTask task) {
        if (task.getCreatedAt() == null) task.setCreatedAt(LocalDateTime.now());
        if (task.getStatus() == null) task.setStatus("pending");
        if (task.getProgress() == null) task.setProgress(0);
        AnalysisTask existing = findByTaskId(task.getTaskId());
        if (existing == null) {
            insert(task);
        } else {
            task.setId(existing.getId());
            update(task);
        }
        return task;
    }
}
