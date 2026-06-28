package io.leavesfly.alphaforge.infrastructure.persistence.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.CashLedgerEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 现金流水数据访问层
 */
@Mapper
public interface CashLedgerRepository {

    void insert(CashLedgerEntry entry);

    CashLedgerEntry findById(@Param("id") Long id);

    List<CashLedgerEntry> findAll();

    List<CashLedgerEntry> findByAccountId(@Param("accountId") Long accountId);

    List<CashLedgerEntry> findByDirection(@Param("direction") String direction);

    void deleteById(@Param("id") Long id);

    void deleteByAccountId(@Param("accountId") Long accountId);

    long count();

    default CashLedgerEntry save(CashLedgerEntry entry) {
        if (entry.getCreatedAt() == null) entry.setCreatedAt(LocalDateTime.now());
        insert(entry);
        return entry;
    }
}
