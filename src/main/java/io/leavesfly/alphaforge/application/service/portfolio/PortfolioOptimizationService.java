package io.leavesfly.alphaforge.application.service.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.repository.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.portfolio.OptimizationObjective;
import io.leavesfly.alphaforge.domain.service.portfolio.PortfolioOptimizationResult;
import io.leavesfly.alphaforge.domain.service.portfolio.PortfolioOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 组合优化应用服务。
 *
 * <p>负责拉取标的历史行情、对齐交易日、计算日收益率矩阵，调用
 * {@link PortfolioOptimizer} 得到最优权重，并结合资金规模给出可执行的建仓股数。</p>
 */
@Service
public class PortfolioOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizationService.class);

    /** 计算协方差所需的最少收益样本数 */
    private static final int MIN_SAMPLES = 20;

    private final MarketDataPort marketData;
    private final PortfolioRepository portfolioRepo;
    private final PortfolioOptimizer optimizer;

    public PortfolioOptimizationService(MarketDataPort marketData,
                                        PortfolioRepository portfolioRepo,
                                        PortfolioOptimizer optimizer) {
        this.marketData = marketData;
        this.portfolioRepo = portfolioRepo;
        this.optimizer = optimizer;
    }

    /**
     * 对指定标的做组合优化。
     *
     * @param codes             标的代码；为空时使用当前组合持仓
     * @param objectiveName     优化目标（见 {@link OptimizationObjective}）
     * @param lookbackDays      回看自然日数（用于拉取历史）
     * @param capital           可投资金额（&gt;0 时输出建议股数）
     * @param riskAversion      风险厌恶系数（MEAN_VARIANCE 使用）
     * @param annualRiskFreeRate 年化无风险利率
     */
    public Map<String, Object> optimize(List<String> codes, String objectiveName, int lookbackDays,
                                        double capital, double riskAversion, double annualRiskFreeRate) {
        OptimizationObjective objective = OptimizationObjective.fromString(objectiveName, OptimizationObjective.MAX_SHARPE);

        List<String> symbols = (codes == null || codes.isEmpty()) ? currentHoldingCodes() : dedup(codes);
        if (symbols.size() < 2) {
            return Map.of("error", "组合优化至少需要 2 个标的", "symbols", symbols);
        }

        int days = lookbackDays > 0 ? lookbackDays : 180;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        // 拉取各标的收盘价序列（date -> close）
        Map<String, TreeMap<LocalDate, Double>> priceSeries = new LinkedHashMap<>();
        Map<String, Double> latestPrice = new LinkedHashMap<>();
        for (String code : symbols) {
            List<StockDailyData> bars = marketData.getHistoryData(code, start, end);
            if (bars == null || bars.isEmpty()) {
                log.warn("组合优化：标的 {} 无历史数据，将被剔除", code);
                continue;
            }
            TreeMap<LocalDate, Double> series = new TreeMap<>();
            for (StockDailyData bar : bars) {
                if (bar.getTradeDate() != null && bar.getClosePrice() != null && bar.getClosePrice() > 0) {
                    series.put(bar.getTradeDate(), bar.getClosePrice());
                }
            }
            if (!series.isEmpty()) {
                priceSeries.put(code, series);
                latestPrice.put(code, series.lastEntry().getValue());
            }
        }

        List<String> valid = new ArrayList<>(priceSeries.keySet());
        if (valid.size() < 2) {
            return Map.of("error", "有效标的不足 2 个（历史数据缺失）", "symbols", valid);
        }

        // 对齐公共交易日
        List<LocalDate> commonDates = intersectDates(priceSeries);
        if (commonDates.size() < MIN_SAMPLES + 1) {
            return Map.of("error", "公共交易日不足（需 ≥ " + (MIN_SAMPLES + 1) + " 天），实际 " + commonDates.size(),
                    "symbols", valid);
        }

        // 构造日收益率矩阵 returns[asset][t]
        double[][] returns = new double[valid.size()][commonDates.size() - 1];
        for (int i = 0; i < valid.size(); i++) {
            TreeMap<LocalDate, Double> series = priceSeries.get(valid.get(i));
            for (int t = 1; t < commonDates.size(); t++) {
                double prev = series.get(commonDates.get(t - 1));
                double cur = series.get(commonDates.get(t));
                returns[i][t - 1] = prev > 0 ? (cur - prev) / prev : 0;
            }
        }

        PortfolioOptimizationResult result = optimizer.optimize(valid, returns, objective, riskAversion, annualRiskFreeRate);
        return buildResponse(result, latestPrice, capital, commonDates);
    }

    private Map<String, Object> buildResponse(PortfolioOptimizationResult result,
                                              Map<String, Double> latestPrice,
                                              double capital, List<LocalDate> commonDates) {
        List<Map<String, Object>> allocations = new ArrayList<>();
        double[] w = result.weights();
        List<String> symbols = result.symbols();
        for (int i = 0; i < symbols.size(); i++) {
            String code = symbols.get(i);
            Map<String, Object> alloc = new LinkedHashMap<>();
            alloc.put("code", code);
            alloc.put("weight", round(w[i], 6));
            alloc.put("risk_contribution", round(result.riskContributions()[i], 6));
            if (capital > 0) {
                double price = latestPrice.getOrDefault(code, 0.0);
                double targetValue = capital * w[i];
                // A 股按手（100 股）取整
                long shares = price > 0 ? Math.round(targetValue / price / 100.0) * 100L : 0;
                alloc.put("price", round(price, 3));
                alloc.put("target_value", round(targetValue, 2));
                alloc.put("suggested_shares", shares);
                alloc.put("actual_value", round(shares * price, 2));
            }
            allocations.add(alloc);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("objective", result.objective().name());
        resp.put("sample_days", commonDates.size() - 1);
        resp.put("date_range", Map.of(
                "start", commonDates.get(0).toString(),
                "end", commonDates.get(commonDates.size() - 1).toString()));
        resp.put("expected_annual_return", round(result.expectedReturn(), 6));
        resp.put("expected_annual_volatility", round(result.expectedVolatility(), 6));
        resp.put("sharpe_ratio", round(result.sharpeRatio(), 4));
        resp.put("diversification_ratio", round(result.diversification(), 4));
        resp.put("allocations", allocations);
        if (capital > 0) resp.put("capital", capital);
        return resp;
    }

    private List<String> currentHoldingCodes() {
        List<String> codes = new ArrayList<>();
        for (PortfolioPosition pos : portfolioRepo.findAll()) {
            if (pos.getStockCode() != null && !codes.contains(pos.getStockCode())) {
                codes.add(pos.getStockCode());
            }
        }
        return codes;
    }

    private List<String> dedup(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (String c : codes) {
            if (c != null && !c.isBlank() && !out.contains(c.trim())) {
                out.add(c.trim());
            }
        }
        return out;
    }

    /** 求所有标的收盘价序列的公共交易日（升序） */
    private List<LocalDate> intersectDates(Map<String, TreeMap<LocalDate, Double>> priceSeries) {
        List<LocalDate> common = null;
        for (TreeMap<LocalDate, Double> series : priceSeries.values()) {
            if (common == null) {
                common = new ArrayList<>(series.keySet());
            } else {
                common.retainAll(series.keySet());
            }
        }
        return common == null ? List.of() : common;
    }

    private double round(double v, int scale) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        double factor = Math.pow(10, scale);
        return Math.round(v * factor) / factor;
    }
}
