package io.leavesfly.alphaforge.infrastructure.persistence.screening;

import io.leavesfly.alphaforge.domain.model.entity.screening.AlphaSiftTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AlphaSift选股任务数据访问层
 */
@Mapper
public interface AlphaSiftTaskRepository {

    void insert(AlphaSiftTask task);

    void update(AlphaSiftTask task);

    AlphaSiftTask findByTaskId(@Param("taskId") String taskId);

    List<AlphaSiftTask> findRecent(@Param("limit") int limit);

    List<AlphaSiftTask> findByStatus(@Param("status") String status);

    void updateStatus(@Param("taskId") String taskId, @Param("status") String status,
                      @Param("progress") Integer progress);

    void updateCompleted(@Param("taskId") String taskId, @Param("status") String status,
                         @Param("resultStocks") String resultStocks, @Param("resultCount") Integer resultCount,
                         @Param("errorMessage") String errorMessage, @Param("completedAt") LocalDateTime completedAt);

    int count();
}
