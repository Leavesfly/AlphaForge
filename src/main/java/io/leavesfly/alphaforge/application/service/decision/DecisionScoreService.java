package io.leavesfly.alphaforge.application.service.decision;

import io.leavesfly.alphaforge.application.service.market.MarketConstants;
import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import io.leavesfly.alphaforge.config.DecisionConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import io.leavesfly.alphaforge.domain.repository.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.domain.service.decision.LightsResult;
import io.leavesfly.alphaforge.domain.service.decision.ThreeLightsEngine;
import io.leavesfly.alphaforge.domain.service.decision.ThreeLightsInput;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 买点三灯评分服务 — 编排数据获取与画像注入，评估本体委托纯 Java 的
 * {@link ThreeLightsEngine}。
 *
 * <p>估值（PE/PB 分位）数据缺口按诚实降级处理：价灯灰灯，结论明示仅基于势/时。
 * 持仓成本 cost 显式传入时进入持仓联动视角。</p>
 */
@Service
public class DecisionScoreService {

    private static final Logger log = LoggerFactory.getLogger(DecisionScoreService.class);

    private final MarketDataPort marketDataPort;
    private final UserRiskProfileService profileService;
    private final DecisionConfig config;
    private final PortfolioRepository portfolioRepository;

    public DecisionScoreService(MarketDataPort marketDataPort,
                                UserRiskProfileService profileService,
                                DecisionConfig config,
                                PortfolioRepository portfolioRepository) {
        this.marketDataPort = marketDataPort;
        this.profileService = profileService;
        this.config = config;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * 对单标的执行买点三灯评估。
     *
     * @param stockCode 股票代码（如 600519）
     * @param cost      持仓成本价（可空：显式覆盖成本；未传时自动取登记持仓成本）
     */
    public LightsResult score(String stockCode, Double cost) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        stockCode = stockCode.trim();

        // 持仓成本解析：显式 cost 优先，其次自动取登记持仓（Phase 3 联动）
        Double effectiveCost = cost;
        Double positionShares = null;
        String positionSource = "manual";
        if (!(cost != null && cost > 0)) {
            try {
                PortfolioPosition held = portfolioRepository.findByStockCode(stockCode).orElse(null);
                if (held != null && held.getCostPrice() != null && held.getCostPrice() > 0) {
                    effectiveCost = held.getCostPrice();
                    positionShares = held.getQuantity() != null
                            ? held.getQuantity().doubleValue() : null;
                    positionSource = "portfolio";
                }
            } catch (Exception e) {
                log.warn("[{}] 持仓查询失败，忽略持仓联动: {}", stockCode, e.getMessage());
            }
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(config.getHistoryDays());
        List<StockDailyData> history = marketDataPort.getHistoryData(stockCode, startDate, endDate);

        MarketType market = MarketType.detectFromCode(stockCode);
        String stockName = !history.isEmpty() ? history.get(history.size() - 1).getStockName() : null;

        // 基准指数历史（仅 A 股拉取，供势灯相对强度与大盘 risk-off 判定）
        List<StockDailyData> benchmarkHistory = null;
        if (market == MarketType.A) {
            try {
                benchmarkHistory = marketDataPort.getHistoryData(config.getBenchmarkCode(),
                        startDate, endDate);
            } catch (Exception e) {
                log.warn("[{}] 基准指数 {} 获取失败，相对强度降级（权重并入动量）: {}",
                        stockCode, config.getBenchmarkCode(), e.getMessage());
            }
        }

        // 用户风险画像：仓位乘数 + 可用资金（万元 → 元）
        Map<String, Object> profileView = profileService.getProfileView();
        double riskMultiplier = (Double) profileView.get("positionMultiplier");
        Double capitalYuan = profileView.get("capitalAmount") instanceof Number capital
                ? capital.doubleValue() * 10_000.0 : null;

        Map<String, Object> marketContext = new LinkedHashMap<>();
        marketContext.put("market", market.name());
        if (market == MarketType.A) {
            marketContext.put("benchmarkCode", config.getBenchmarkCode());
            marketContext.put("benchmarkName",
                    MarketConstants.MARKET_INDICES.getOrDefault(config.getBenchmarkCode(), "基准指数"));
        }

        ThreeLightsInput input = ThreeLightsInput.builder(stockCode)
                .stockName(stockName)
                .history(history)
                .benchmarkHistory(benchmarkHistory)
                // 估值数据缺口：价灯诚实降级为灰（实时行情未含 PE/PB，历史分位属后续 backlog）
                .valuationNote("实时行情源未提供 PE/PB 估值分位：价维度无法评估，结论仅基于势/时")
                .positionCost(effectiveCost)
                .positionShares(positionShares)
                .positionSource(positionSource)
                .riskMultiplier(riskMultiplier)
                .capitalYuan(capitalYuan)
                .lotSize(market == MarketType.A ? 100 : 1)
                .maxPositionPct(config.getMaxPositionPct())
                .marketContext(marketContext)
                .build();

        LightsResult result = ThreeLightsEngine.evaluate(input);
        log.info("[{}] 三灯评估完成: {} → {}（{} 根 K 线, 基准 {}）",
                stockCode, result.lightsSummary(), result.getVerdict().getCn(),
                result.getNBars(), benchmarkHistory != null ? config.getBenchmarkCode() : "无");
        return result;
    }
}
