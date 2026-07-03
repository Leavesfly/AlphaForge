package io.leavesfly.alphaforge.application.factor.analysis;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.factor.ClassicFactorLibrary;
import io.leavesfly.alphaforge.domain.service.factor.CrossSectionalOps;
import io.leavesfly.alphaforge.domain.service.factor.FactorLayerAnalyzer;
import io.leavesfly.alphaforge.domain.service.factor.FactorLayerResult;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经典因子分析应用服务。
 *
 * <p>对给定股票池计算经典因子，做横截面去极值 + 标准化预处理后，运行分层回测与
 * IC 分析，输出因子有效性报告（分层收益、多空、单调性、IC-IR）。</p>
 */
@Service
public class FactorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FactorAnalysisService.class);

    private final MarketDataPort marketData;
    private final ClassicFactorLibrary factorLibrary;
    private final FactorLayerAnalyzer layerAnalyzer;

    public FactorAnalysisService(MarketDataPort marketData,
                                 ClassicFactorLibrary factorLibrary,
                                 FactorLayerAnalyzer layerAnalyzer) {
        this.marketData = marketData;
        this.factorLibrary = factorLibrary;
        this.layerAnalyzer = layerAnalyzer;
    }

    public List<String> availableFactors() {
        return factorLibrary.names();
    }

    /**
     * 分析单个经典因子。
     *
     * @param codes       股票池代码
     * @param factorName  因子名（见 {@link ClassicFactorLibrary#FACTOR_NAMES}）
     * @param lookbackDays 历史回看自然日
     * @param forwardDays 前瞻收益天数（如 5）
     * @param quantiles   分层数（如 5）
     */
    public Map<String, Object> analyze(List<String> codes, String factorName,
                                       int lookbackDays, int forwardDays, int quantiles) {
        if (!factorLibrary.supports(factorName)) {
            return Map.of("error", "不支持的因子: " + factorName, "available", factorLibrary.names());
        }
        if (codes == null || codes.size() < quantiles) {
            return Map.of("error", "股票池数量需 ≥ 分层数(" + quantiles + ")");
        }
        int fwd = forwardDays > 0 ? forwardDays : 5;
        int days = lookbackDays > 0 ? lookbackDays : 250;

        Map<String, List<StockDailyData>> universe = loadUniverse(codes, days);
        if (universe.size() < quantiles) {
            return Map.of("error", "有效标的不足（历史数据缺失），实际 " + universe.size());
        }

        // 按日期聚合横截面 (因子值, 前瞻收益)
        Map<String, List<double[]>> byDate = new LinkedHashMap<>();
        for (List<StockDailyData> history : universe.values()) {
            if (history.size() < fwd + 2) continue;
            for (int i = 1; i < history.size() - fwd; i++) {
                double fv = factorLibrary.compute(factorName, history.subList(0, i + 1));
                if (Double.isNaN(fv)) continue;
                Double cur = history.get(i).getClosePrice();
                Double fut = history.get(i + fwd).getClosePrice();
                if (cur == null || fut == null || cur <= 0) continue;
                double fr = (fut - cur) / cur;
                String date = history.get(i).getTradeDate() != null
                        ? history.get(i).getTradeDate().toString() : "d" + i;
                byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(new double[]{fv, fr});
            }
        }

        // 逐期预处理（MAD 去极值 + z-score 标准化），组织成分层输入
        List<List<double[]>> periods = new ArrayList<>();
        for (List<double[]> pairs : byDate.values()) {
            if (pairs.size() < quantiles) continue;
            int n = pairs.size();
            double[] fv = new double[n];
            for (int i = 0; i < n; i++) fv[i] = pairs.get(i)[0];
            double[] processed = CrossSectionalOps.zscore(CrossSectionalOps.winsorizeMad(fv, 3.0));
            List<double[]> period = new ArrayList<>(n);
            for (int i = 0; i < n; i++) period.add(new double[]{processed[i], pairs.get(i)[1]});
            periods.add(period);
        }

        if (periods.isEmpty()) {
            return Map.of("error", "有效横截面为空，无法分层回测");
        }

        FactorLayerResult result = layerAnalyzer.analyze(periods, quantiles);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("factor", factorName);
        resp.put("universe_size", universe.size());
        resp.put("forward_days", fwd);
        resp.put("lookback_days", days);
        resp.putAll(result.toMap());
        resp.put("interpretation", interpret(result));
        return resp;
    }

    private String interpret(FactorLayerResult r) {
        String strength = Math.abs(r.icMean()) >= 0.05 ? "强"
                : Math.abs(r.icMean()) >= 0.02 ? "中等" : "弱";
        String direction = r.icMean() >= 0 ? "正向（因子值越大未来收益越高）" : "反向（因子值越大未来收益越低）";
        String mono = Math.abs(r.monotonicity()) >= 0.8 ? "分层单调性良好" : "分层单调性一般";
        return String.format("IC=%.4f，IR=%.2f，属%s%s；%s。", r.icMean(), r.icIR(), strength, direction, mono);
    }

    private Map<String, List<StockDailyData>> loadUniverse(List<String> codes, int days) {
        Map<String, List<StockDailyData>> universe = new LinkedHashMap<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        for (String code : codes) {
            if (code == null || code.isBlank()) continue;
            try {
                List<StockDailyData> data = marketData.getHistoryData(code.trim(), start, end);
                if (data != null && !data.isEmpty()) {
                    universe.put(code.trim(), data);
                }
            } catch (Exception e) {
                log.debug("因子分析：获取 {} 历史失败: {}", code, e.getMessage());
            }
        }
        return universe;
    }
}
