package io.leavesfly.alphaforge.infrastructure.persistence.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.CorporateAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公司行动数据访问层
 */
@Mapper
public interface CorporateActionRepository {

    void insert(CorporateAction action);

    CorporateAction findById(@Param("id") Long id);

    List<CorporateAction> findAll();

    List<CorporateAction> findByAccountId(@Param("accountId") Long accountId);

    List<CorporateAction> findBySymbol(@Param("symbol") String symbol);

    void deleteById(@Param("id") Long id);

    long count();

    default CorporateAction save(CorporateAction action) {
        if (action.getCreatedAt() == null) action.setCreatedAt(LocalDateTime.now());
        insert(action);
        return action;
    }
}
