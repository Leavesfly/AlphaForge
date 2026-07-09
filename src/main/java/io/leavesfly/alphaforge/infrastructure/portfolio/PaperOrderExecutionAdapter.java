package io.leavesfly.alphaforge.infrastructure.portfolio;

import io.leavesfly.alphaforge.application.service.portfolio.PaperTradingService;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.repository.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.domain.service.port.OrderExecutionPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 纸面交易适配器 — 包装 PaperTradingService。
 */
@Component
public class PaperOrderExecutionAdapter implements OrderExecutionPort {

    private final PaperTradingService paperTradingService;
    private final PortfolioRepository positionRepo;

    public PaperOrderExecutionAdapter(PaperTradingService paperTradingService,
                                      PortfolioRepository positionRepo) {
        this.paperTradingService = paperTradingService;
        this.positionRepo = positionRepo;
    }

    @Override
    public Map<String, Object> buy(Long accountId, String stockCode, int quantity) {
        return paperTradingService.buy(accountId, stockCode, quantity);
    }

    @Override
    public Map<String, Object> sell(Long accountId, String stockCode, int quantity) {
        return paperTradingService.sell(accountId, stockCode, quantity);
    }

    @Override
    public Map<String, Object> getAccountEquity(Long accountId) {
        return paperTradingService.getAccountDetail(accountId);
    }

    @Override
    public int getPositionQuantity(Long accountId, String stockCode) {
        PortfolioPosition pos = positionRepo.findByAccountIdAndStockCode(accountId, stockCode);
        return pos != null && pos.getQuantity() != null ? pos.getQuantity() : 0;
    }
}
