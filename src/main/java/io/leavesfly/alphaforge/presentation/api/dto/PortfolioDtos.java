package io.leavesfly.alphaforge.presentation.api.dto;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.CashLedgerEntry;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.CorporateAction;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioAccount;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioTrade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投资组合响应 DTO 集合。
 *
 * <p>用于隔离 API 出参与领域实体：控制器在边界处将领域实体映射为下列 DTO，
 * 避免领域模型结构变化直接冲击对外 API 契约。</p>
 */
public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    /** 持仓响应 DTO */
    public record PositionDto(Long id, Long accountId, String stockCode, String stockName, String market,
                              Integer quantity, Double costPrice, Double currentPrice, Double profitLoss,
                              Double profitLossPct, Double marketValue, Double positionPct, LocalDateTime buyDate,
                              Double stopLossPrice, Double targetPrice, String tags, String note,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {

        public static PositionDto from(PortfolioPosition p) {
            return new PositionDto(p.getId(), p.getAccountId(), p.getStockCode(), p.getStockName(), p.getMarket(),
                    p.getQuantity(), p.getCostPrice(), p.getCurrentPrice(), p.getProfitLoss(), p.getProfitLossPct(),
                    p.getMarketValue(), p.getPositionPct(), p.getBuyDate(), p.getStopLossPrice(), p.getTargetPrice(),
                    p.getTags(), p.getNote(), p.getCreatedAt(), p.getUpdatedAt());
        }

        public static List<PositionDto> from(List<PortfolioPosition> list) {
            return list == null ? List.of() : list.stream().map(PositionDto::from).toList();
        }
    }

    /** 账户响应 DTO */
    public record AccountDto(Long id, String name, String broker, String market, String baseCurrency,
                             Double cashBalance, Double loanBalance, Double loanLimit, String ownerId,
                             Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {

        public static AccountDto from(PortfolioAccount a) {
            return new AccountDto(a.getId(), a.getName(), a.getBroker(), a.getMarket(), a.getBaseCurrency(),
                    a.getCashBalance(), a.getLoanBalance(), a.getLoanLimit(), a.getOwnerId(), a.getIsActive(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }

        public static List<AccountDto> from(List<PortfolioAccount> list) {
            return list == null ? List.of() : list.stream().map(AccountDto::from).toList();
        }
    }

    /** 交易记录响应 DTO */
    public record TradeDto(Long id, Long accountId, String symbol, LocalDate tradeDate, String side,
                           Double quantity, Double price, Double fee, Double tax, String market, String currency,
                           String tradeUid, String note, LocalDateTime createdAt) {

        public static TradeDto from(PortfolioTrade t) {
            return new TradeDto(t.getId(), t.getAccountId(), t.getSymbol(), t.getTradeDate(), t.getSide(),
                    t.getQuantity(), t.getPrice(), t.getFee(), t.getTax(), t.getMarket(), t.getCurrency(),
                    t.getTradeUid(), t.getNote(), t.getCreatedAt());
        }

        public static List<TradeDto> from(List<PortfolioTrade> list) {
            return list == null ? List.of() : list.stream().map(TradeDto::from).toList();
        }
    }

    /** 资金流水响应 DTO */
    public record CashLedgerDto(Long id, Long accountId, LocalDate eventDate, String direction, Double amount,
                                String currency, String note, LocalDateTime createdAt) {

        public static CashLedgerDto from(CashLedgerEntry e) {
            return new CashLedgerDto(e.getId(), e.getAccountId(), e.getEventDate(), e.getDirection(), e.getAmount(),
                    e.getCurrency(), e.getNote(), e.getCreatedAt());
        }

        public static List<CashLedgerDto> from(List<CashLedgerEntry> list) {
            return list == null ? List.of() : list.stream().map(CashLedgerDto::from).toList();
        }
    }

    /** 公司行动响应 DTO */
    public record CorporateActionDto(Long id, Long accountId, String symbol, LocalDate effectiveDate,
                                     String actionType, String market, String currency, Double cashDividendPerShare,
                                     Double splitRatio, String note, LocalDateTime createdAt) {

        public static CorporateActionDto from(CorporateAction c) {
            return new CorporateActionDto(c.getId(), c.getAccountId(), c.getSymbol(), c.getEffectiveDate(),
                    c.getActionType(), c.getMarket(), c.getCurrency(), c.getCashDividendPerShare(),
                    c.getSplitRatio(), c.getNote(), c.getCreatedAt());
        }

        public static List<CorporateActionDto> from(List<CorporateAction> list) {
            return list == null ? List.of() : list.stream().map(CorporateActionDto::from).toList();
        }
    }
}
