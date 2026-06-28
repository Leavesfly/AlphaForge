package io.leavesfly.stock.application.service.alert;

import io.leavesfly.stock.domain.service.port.MarketDataPort;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.TechnicalIndicatorCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 告警指标计算器（应用编排层）
 *
 * 仅负责"取数 → 转换为数值数组 → 委托领域算法"的编排，
 * 指标算法本身由 {@link TechnicalIndicatorCalculator}（领域层，零外部依赖）提供，
 * 避免与 TechnicalAnalysisService 重复实现。
 */
@Service
public class AlertIndicators {

    private static final Logger log = LoggerFactory.getLogger(AlertIndicators.class);
    private final MarketDataPort dataFetcher;
    private final TechnicalIndicatorCalculator calculator;

    public AlertIndicators(MarketDataPort dataFetcher, TechnicalIndicatorCalculator calculator) {
        this.dataFetcher = dataFetcher;
        this.calculator = calculator;
    }

    /** 量比(今日成交量 / 5日均量) */
    public double volumeRatio(String stockCode) {
        long[] volumes = toVolumes(getRecentData(stockCode, 10));
        return calculator.volumeRatio(volumes);
    }

    /** RSI指标 */
    public double rsi(String stockCode, int period) {
        double[] closes = toCloses(getRecentData(stockCode, period + 5));
        return calculator.rsi(closes, period);
    }

    /** MACD金叉判断(DIF上穿DEA) */
    public boolean isMacdGoldenCross(String stockCode) {
        double[] closes = toCloses(getRecentData(stockCode, 40));
        return calculator.isMacdGoldenCross(closes);
    }

    /** MACD死叉判断(DIF下穿DEA) */
    public boolean isMacdDeathCross(String stockCode) {
        double[] closes = toCloses(getRecentData(stockCode, 40));
        return calculator.isMacdDeathCross(closes);
    }

    /** 均线突破(收盘价突破N日均线) */
    public boolean isMaBreakout(String stockCode, int period) {
        double[] closes = toCloses(getRecentData(stockCode, period + 2));
        return calculator.isMaBreakout(closes, period);
    }

    // ========== 数据获取与转换（应用编排） ==========

    private List<StockDailyData> getRecentData(String stockCode, int days) {
        try {
            return dataFetcher.getHistoryData(stockCode, LocalDate.now().minusDays(days + 5), LocalDate.now());
        } catch (Exception e) {
            return List.of();
        }
    }

    private double[] toCloses(List<StockDailyData> data) {
        return data.stream().mapToDouble(d -> d.getClosePrice() != null ? d.getClosePrice() : 0).toArray();
    }

    private long[] toVolumes(List<StockDailyData> data) {
        return data.stream().mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0).toArray();
    }
}
