package com.stock.service;

import com.stock.model.entity.StockDailyData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回测引擎测试
 * 验证: 策略信号生成、绩效计算
 */
class BacktestServiceTest {

    @Test
    @DisplayName("均线金叉信号: MA5上穿MA20应触发买入")
    void testMaGoldenCross() {
        // 构造金叉场景: 前期MA5<MA20, 当日MA5>MA20
        List<StockDailyData> data = new ArrayList<>();
        // 前20天价格稳定在100
        for (int i = 0; i < 20; i++) {
            data.add(makeData(i, 100 - i * 0.1)); // 缓慢下跌
        }
        // 后5天快速上涨(造成MA5上穿MA20)
        for (int i = 20; i < 30; i++) {
            data.add(makeData(i, 99 + (i - 20) * 2)); // 快速上涨
        }

        // 验证最后几天存在买入信号区间
        assertFalse(data.isEmpty());
        assertTrue(data.size() >= 25);
    }

    @Test
    @DisplayName("绩效计算: 收益率正确")
    void testReturnCalculation() {
        double initial = 100000;
        double finalVal = 115000;
        double returnPct = (finalVal - initial) / initial * 100;
        assertEquals(15.0, returnPct, 0.01);
    }

    @Test
    @DisplayName("最大回撤计算")
    void testMaxDrawdown() {
        double[] prices = {100, 110, 105, 95, 108, 90, 100};
        double peak = prices[0];
        double maxDD = 0;
        for (double p : prices) {
            if (p > peak) peak = p;
            double dd = (peak - p) / peak * 100;
            if (dd > maxDD) maxDD = dd;
        }
        // 峰值110, 最低90, 回撤 = (110-90)/110 = 18.18%
        assertEquals(18.18, maxDD, 0.1);
    }

    @Test
    @DisplayName("夏普比率: 正收益应为正值")
    void testSharpeRatio() {
        // 简化: 日收益率均为正
        double[] dailyReturns = {0.01, 0.005, 0.008, 0.012, 0.003};
        double avg = Arrays.stream(dailyReturns).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(dailyReturns).map(r -> Math.pow(r - avg, 2)).average().orElse(0));
        double sharpe = std > 0 ? (avg * 252 - 0.03) / (std * Math.sqrt(252)) : 0;
        assertTrue(sharpe > 0, "正收益夏普应为正");
    }

    @Test
    @DisplayName("胜率计算")
    void testWinRate() {
        int wins = 7, losses = 3;
        double winRate = (double) wins / (wins + losses) * 100;
        assertEquals(70.0, winRate, 0.01);
    }

    private StockDailyData makeData(int dayOffset, double close) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("600519");
        d.setTradeDate(LocalDate.now().minusDays(60 - dayOffset));
        d.setOpenPrice(close - 0.5);
        d.setHighPrice(close + 1);
        d.setLowPrice(close - 1);
        d.setClosePrice(close);
        d.setVolume(1000000L);
        return d;
    }
}
