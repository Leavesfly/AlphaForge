package com.stock.repository;

import com.stock.model.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 投资组合数据访问层
 */
@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioPosition, Long> {
    List<PortfolioPosition> findAllByOrderByUpdatedAtDesc();
    Optional<PortfolioPosition> findByStockCode(String stockCode);
    List<PortfolioPosition> findByMarketOrderByMarketValueDesc(String market);
    @Query("SELECT SUM(p.marketValue) FROM PortfolioPosition p")
    Double getTotalMarketValue();
    @Query("SELECT SUM(p.profitLoss) FROM PortfolioPosition p")
    Double getTotalProfitLoss();
    long countByProfitLossPctGreaterThan(Double pct);
    long countByProfitLossPctLessThan(Double pct);
}
