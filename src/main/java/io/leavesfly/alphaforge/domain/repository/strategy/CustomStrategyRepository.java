package io.leavesfly.alphaforge.domain.repository.strategy;

import io.leavesfly.alphaforge.domain.model.entity.strategy.CustomStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自定义策略数据访问层
 */
@Mapper
public interface CustomStrategyRepository {

    void insert(CustomStrategy strategy);

    void update(CustomStrategy strategy);

    void deleteByStrategyId(@Param("strategyId") String strategyId);

    CustomStrategy findByStrategyId(@Param("strategyId") String strategyId);

    List<CustomStrategy> findAll();

    List<CustomStrategy> findByLifecycleState(@Param("lifecycleState") String lifecycleState);

    boolean existsByStrategyId(@Param("strategyId") String strategyId);

    int count();

    void insertVersion(@Param("strategyId") String strategyId, @Param("version") int version,
                       @Param("yamlContent") String yamlContent, @Param("label") String label,
                       @Param("description") String description, @Param("changeNote") String changeNote);

    List<CustomStrategy> findVersions(@Param("strategyId") String strategyId);
}
