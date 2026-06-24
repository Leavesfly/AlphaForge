package io.leavesfly.stock.application.service;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.domain.model.entity.PortfolioPosition;
import io.leavesfly.stock.infrastructure.persistence.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 投资组合服务
 * 
 * 对应Python版本的 src/services/portfolio_service.py
 * 功能: 持仓管理、盈亏计算、风险评估、仓位分析
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private final PortfolioRepository portfolioRepo;
    private final DataFetcherManager dataFetcher;

    public PortfolioService(PortfolioRepository portfolioRepo, DataFetcherManager dataFetcher) {
        this.portfolioRepo = portfolioRepo;
        this.dataFetcher = dataFetcher;
    }

    /** 获取所有持仓 */
    public List<PortfolioPosition> getAllPositions() {
        return portfolioRepo.findAllByOrderByUpdatedAtDesc();
    }

    /** 添加持仓 */
    public PortfolioPosition addPosition(PortfolioPosition position) {
        // 检查是否已存在该股票持仓
        Optional<PortfolioPosition> existing = portfolioRepo.findByStockCode(position.getStockCode());
        if (existing.isPresent()) {
            // 合并持仓: 加权平均成本价
            PortfolioPosition p = existing.get();
            int totalQty = p.getQuantity() + position.getQuantity();
            double avgCost = (p.getCostPrice() * p.getQuantity() + position.getCostPrice() * position.getQuantity()) / totalQty;
            p.setQuantity(totalQty);
            p.setCostPrice(avgCost);
            return portfolioRepo.save(p);
        }
        return portfolioRepo.save(position);
    }

    /** 减仓/清仓 */
    public PortfolioPosition reducePosition(String stockCode, int quantity) {
        return portfolioRepo.findByStockCode(stockCode).map(p -> {
            int newQty = p.getQuantity() - quantity;
            if (newQty <= 0) {
                portfolioRepo.delete(p);
                return null;
            }
            p.setQuantity(newQty);
            return portfolioRepo.save(p);
        }).orElse(null);
    }

    /** 删除持仓 */
    public void deletePosition(Long id) {
        portfolioRepo.deleteById(id);
    }

    /**
     * 更新所有持仓的实时数据和盈亏
     */
    public void refreshPositions() {
        List<PortfolioPosition> positions = portfolioRepo.findAll();
        double totalValue = 0;

        for (PortfolioPosition p : positions) {
            try {
                Map<String, Object> quote = dataFetcher.getRealtimeQuote(p.getStockCode());
                if (quote != null && !quote.isEmpty()) {
                    Object priceObj = quote.get("current_price");
                    if (priceObj instanceof Number) {
                        double currentPrice = ((Number) priceObj).doubleValue();
                        p.setCurrentPrice(currentPrice);
                        double marketValue = currentPrice * p.getQuantity();
                        p.setMarketValue(marketValue);
                        double profitLoss = (currentPrice - p.getCostPrice()) * p.getQuantity();
                        p.setProfitLoss(profitLoss);
                        p.setProfitLossPct((currentPrice - p.getCostPrice()) / p.getCostPrice() * 100);
                        totalValue += marketValue;
                    }
                }
            } catch (Exception e) {
                log.error("刷新持仓失败: {}", p.getStockCode());
            }
        }

        // 计算仓位占比
        for (PortfolioPosition p : positions) {
            if (totalValue > 0 && p.getMarketValue() != null) {
                p.setPositionPct(p.getMarketValue() / totalValue * 100);
            }
        }
        portfolioRepo.saveAll(positions);
    }

    /** 获取投资组合概要 */
    public Map<String, Object> getPortfolioSummary() {
        List<PortfolioPosition> positions = portfolioRepo.findAll();
        Map<String, Object> summary = new LinkedHashMap<>();
        
        double totalMarketValue = positions.stream()
                .mapToDouble(p -> p.getMarketValue() != null ? p.getMarketValue() : 0).sum();
        double totalCost = positions.stream()
                .mapToDouble(p -> p.getCostPrice() * p.getQuantity()).sum();
        double totalProfitLoss = positions.stream()
                .mapToDouble(p -> p.getProfitLoss() != null ? p.getProfitLoss() : 0).sum();
        long profitCount = positions.stream()
                .filter(p -> p.getProfitLossPct() != null && p.getProfitLossPct() > 0).count();

        summary.put("total_positions", positions.size());
        summary.put("total_market_value", totalMarketValue);
        summary.put("total_cost", totalCost);
        summary.put("total_profit_loss", totalProfitLoss);
        summary.put("total_return_pct", totalCost > 0 ? (totalMarketValue - totalCost) / totalCost * 100 : 0);
        summary.put("profit_count", profitCount);
        summary.put("loss_count", positions.size() - profitCount);
        summary.put("win_rate_pct", positions.size() > 0 ? (double) profitCount / positions.size() * 100 : 0);
        
        return summary;
    }

    /**
     * 风险评估
     */
    public Map<String, Object> assessRisk() {
        List<PortfolioPosition> positions = portfolioRepo.findAll();
        Map<String, Object> risk = new LinkedHashMap<>();

        // 单只股票集中度
        double totalValue = positions.stream()
                .mapToDouble(p -> p.getMarketValue() != null ? p.getMarketValue() : 0).sum();
        double maxConcentration = positions.stream()
                .mapToDouble(p -> p.getPositionPct() != null ? p.getPositionPct() : 0).max().orElse(0);

        // 亏损持仓比例
        long lossCount = positions.stream()
                .filter(p -> p.getProfitLossPct() != null && p.getProfitLossPct() < -10).count();

        risk.put("max_concentration_pct", maxConcentration);
        risk.put("concentration_risk", maxConcentration > 30 ? "高" : maxConcentration > 20 ? "中" : "低");
        risk.put("deep_loss_count", lossCount);
        risk.put("overall_risk_level", lossCount > 3 || maxConcentration > 40 ? "high" : "medium");
        
        return risk;
    }
}
