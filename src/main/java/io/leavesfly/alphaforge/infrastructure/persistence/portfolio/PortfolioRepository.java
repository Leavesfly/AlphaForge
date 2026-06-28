package io.leavesfly.alphaforge.infrastructure.persistence.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 投资组合数据访问层
 */
@Mapper
public interface PortfolioRepository {

    void insert(PortfolioPosition pos);

    void update(PortfolioPosition pos);

    PortfolioPosition findById(@Param("id") Long id);

    List<PortfolioPosition> findAll();

    void deleteById(@Param("id") Long id);

    List<PortfolioPosition> findAllByOrderByUpdatedAtDesc();

    PortfolioPosition findByStockCodeOne(@Param("stockCode") String stockCode);

    List<PortfolioPosition> findByAccountId(@Param("accountId") Long accountId);

    PortfolioPosition findByAccountIdAndStockCode(@Param("accountId") Long accountId, @Param("stockCode") String stockCode);

    void deleteByAccountId(@Param("accountId") Long accountId);

    List<PortfolioPosition> findByMarketOrderByMarketValueDesc(@Param("market") String market);

    Double getTotalMarketValue();

    Double getTotalProfitLoss();

    long countByProfitLossPctGreaterThan(@Param("pct") Double pct);

    long countByProfitLossPctLessThan(@Param("pct") Double pct);

    default PortfolioPosition save(PortfolioPosition pos) {
        LocalDateTime now = LocalDateTime.now();
        if (pos.getId() == null) {
            if (pos.getCreatedAt() == null) {
                pos.setCreatedAt(now);
            }
            pos.setUpdatedAt(now);
            insert(pos);
        } else {
            pos.setUpdatedAt(now);
            update(pos);
        }
        return pos;
    }

    default void saveAll(List<PortfolioPosition> positions) {
        for (PortfolioPosition pos : positions) {
            save(pos);
        }
    }

    default void delete(PortfolioPosition pos) {
        if (pos != null && pos.getId() != null) {
            deleteById(pos.getId());
        }
    }

    default Optional<PortfolioPosition> findByStockCode(String stockCode) {
        return Optional.ofNullable(findByStockCodeOne(stockCode));
    }

    default Optional<PortfolioPosition> findByAccountIdAndStockCodeOpt(Long accountId, String stockCode) {
        return Optional.ofNullable(findByAccountIdAndStockCode(accountId, stockCode));
    }

    default Optional<PortfolioPosition> findByIdOpt(Long id) {
        return Optional.ofNullable(findById(id));
    }
}
