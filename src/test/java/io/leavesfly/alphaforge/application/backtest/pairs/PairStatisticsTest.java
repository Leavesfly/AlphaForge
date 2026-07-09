package io.leavesfly.alphaforge.application.backtest.pairs;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PairStatistics 配对统计工具测试")
class PairStatisticsTest {

    private StockDailyData bar(String code, LocalDate date, double close) {
        StockDailyData b = new StockDailyData();
        b.setStockCode(code);
        b.setTradeDate(date);
        b.setClosePrice(close);
        b.setVolume(1_000_000L);
        return b;
    }

    @Test
    @DisplayName("按交易日取交集对齐两腿序列")
    void alignByDateTakesIntersection() {
        List<StockDailyData> a = new ArrayList<>();
        List<StockDailyData> b = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            a.add(bar("A", LocalDate.of(2024, 1, i), 10 + i));   // 01-01 ~ 01-05
        }
        for (int i = 3; i <= 7; i++) {
            b.add(bar("B", LocalDate.of(2024, 1, i), 20 + i));   // 01-03 ~ 01-07
        }

        PairStatistics.Aligned aligned = PairStatistics.alignByDate(a, b);

        assertEquals(3, aligned.size(), "交集应为 01-03/04/05 共 3 天");
        assertArrayEquals(new double[]{13, 14, 15}, aligned.getClosesA(), 1e-9);
        assertArrayEquals(new double[]{23, 24, 25}, aligned.getClosesB(), 1e-9);
    }

    @Test
    @DisplayName("完全正相关序列相关系数接近 1")
    void correlationOfLinearSeries() {
        double[] a = {1, 2, 3, 4, 5};
        double[] b = {2, 4, 6, 8, 10};
        assertEquals(1.0, PairStatistics.correlation(a, b), 1e-9);
    }

    @Test
    @DisplayName("OLS 对冲比率等于回归斜率")
    void hedgeRatioEqualsSlope() {
        // a = 2 * b，回归 a = alpha + beta*b，beta 应为 2
        double[] a = {2, 4, 6, 8, 10};
        double[] b = {1, 2, 3, 4, 5};
        assertEquals(2.0, PairStatistics.hedgeRatio(a, b), 1e-9);
    }

    @Test
    @DisplayName("滚动 z-score 用窗口均值与样本标准差标准化")
    void zscoreOverWindow() {
        double[] spread = {0, 0, 0, 0, 0, 10};
        // 窗口 [1..5] = {0,0,0,0,10}, mean=2, sample std=sqrt(20)
        double expected = (10 - 2) / Math.sqrt(20);
        assertEquals(expected, PairStatistics.zscore(spread, 5, 5), 1e-9);
    }

    @Test
    @DisplayName("窗口数据不足时 z-score 返回 0")
    void zscoreInsufficientWindow() {
        double[] spread = {1, 2, 3};
        assertEquals(0.0, PairStatistics.zscore(spread, 1, 5), 1e-9);
    }

    @Test
    @DisplayName("均值回复序列判定为回复且半衰期约为 1")
    void meanReversionDetectsReverting() {
        // spread[i] = 5 * 0.5^i，衰减向 0，AR(1) rho = -0.5
        double[] spread = new double[16];
        for (int i = 0; i < spread.length; i++) {
            spread[i] = 5 * Math.pow(0.5, i);
        }
        PairStatistics.MeanReversion mr = PairStatistics.meanReversionScore(spread);
        assertTrue(mr.meanReverting(), "衰减序列应判定为均值回复");
        assertEquals(-0.5, mr.rho(), 1e-9);
        assertEquals(1.0, mr.halfLife(), 1e-6);
    }

    @Test
    @DisplayName("单调递增序列不判定为均值回复")
    void meanReversionRejectsTrend() {
        double[] spread = new double[16];
        for (int i = 0; i < spread.length; i++) {
            spread[i] = i;
        }
        PairStatistics.MeanReversion mr = PairStatistics.meanReversionScore(spread);
        assertFalse(mr.meanReverting(), "线性趋势序列不应判定为均值回复");
        assertEquals(0.0, mr.rho(), 1e-9);
    }
}
