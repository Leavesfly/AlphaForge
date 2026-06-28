package io.leavesfly.stock.application.service.portfolio;

import io.leavesfly.stock.domain.service.port.MarketDataPort;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.stock.domain.service.port.NotificationPort;
import io.leavesfly.stock.infrastructure.persistence.portfolio.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 组合风险服务 + 组合告警 + 组合导入
 */
@Service
public class PortfolioRiskService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRiskService.class);
    private final PortfolioRepository portfolioRepo;
    private final MarketDataPort dataFetcher;
    private final NotificationPort notificationService;

    public PortfolioRiskService(PortfolioRepository portfolioRepo, MarketDataPort dataFetcher,
                                NotificationPort notificationService) {
        this.portfolioRepo = portfolioRepo;
        this.dataFetcher = dataFetcher;
        this.notificationService = notificationService;
    }

    /** 计算组合整体风险 */
    public Map<String, Object> assessRisk() {
        List<PortfolioPosition> positions = portfolioRepo.findAll();
        Map<String, Object> risk = new LinkedHashMap<>();
        if (positions.isEmpty()) { risk.put("level", "none"); return risk; }

        double totalValue = 0, totalLoss = 0;
        int highRiskCount = 0;
        for (PortfolioPosition pos : positions) {
            double value = pos.getQuantity() * (pos.getCurrentPrice() != null ? pos.getCurrentPrice() : pos.getCostPrice());
            totalValue += value;
            double pnl = (pos.getCurrentPrice() != null ? pos.getCurrentPrice() : pos.getCostPrice()) - pos.getCostPrice();
            if (pnl < 0) totalLoss += Math.abs(pnl) * pos.getQuantity();
            if (pnl / pos.getCostPrice() < -0.1) highRiskCount++;
        }
        double lossRatio = totalValue > 0 ? totalLoss / totalValue * 100 : 0;
        String level = lossRatio > 10 ? "high" : lossRatio > 5 ? "medium" : "low";
        risk.put("level", level);
        risk.put("total_value", totalValue);
        risk.put("loss_ratio_pct", lossRatio);
        risk.put("positions", positions.size());
        risk.put("high_risk_count", highRiskCount);
        risk.put("concentration", positions.size() <= 3 ? "集中" : "分散");
        return risk;
    }

    /** 组合告警检查(每日调度) */
    public void checkPortfolioAlerts() {
        Map<String, Object> risk = assessRisk();
        String level = (String) risk.get("level");
        if ("high".equals(level)) {
            notificationService.sendMessage("⚠️ 组合风险: 高",
                    String.format("亏损比: %.1f%%, 高风险持仓: %d只", risk.get("loss_ratio_pct"), risk.get("high_risk_count")));
        }
    }

    /** 从CSV文本导入持仓 */
    public int importFromCsv(String csvContent) {
        String[] lines = csvContent.split("\n");
        int imported = 0;
        for (int i = 1; i < lines.length; i++) { // 跳过表头
            String[] cols = lines[i].split(",");
            if (cols.length < 3) continue;
            try {
                PortfolioPosition pos = new PortfolioPosition();
                pos.setStockCode(cols[0].trim());
                pos.setStockName(cols.length > 3 ? cols[3].trim() : cols[0].trim());
                pos.setQuantity(Integer.parseInt(cols[1].trim()));
                pos.setCostPrice(Double.parseDouble(cols[2].trim()));
                portfolioRepo.save(pos);
                imported++;
            } catch (Exception e) {
                log.warn("导入行失败: {}", lines[i]);
            }
        }
        return imported;
    }
}
