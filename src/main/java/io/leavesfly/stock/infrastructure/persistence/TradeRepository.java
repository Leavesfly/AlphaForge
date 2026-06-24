package io.leavesfly.stock.infrastructure.persistence;

import io.leavesfly.stock.domain.model.entity.PortfolioTrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易记录数据访问层
 */
@Mapper
public interface TradeRepository {

    void insert(PortfolioTrade trade);

    PortfolioTrade findById(@Param("id") Long id);

    List<PortfolioTrade> findAll();

    List<PortfolioTrade> findByAccountId(@Param("accountId") Long accountId);

    List<PortfolioTrade> findBySymbol(@Param("symbol") String symbol);

    List<PortfolioTrade> findByAccountIdAndSymbol(@Param("accountId") Long accountId, @Param("symbol") String symbol);

    void deleteById(@Param("id") Long id);

    long count();

    default PortfolioTrade save(PortfolioTrade trade) {
        if (trade.getCreatedAt() == null) trade.setCreatedAt(LocalDateTime.now());
        insert(trade);
        return trade;
    }
}
