package io.leavesfly.alphaforge.application.strategy.condition;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BarPatternConditions / 共享形态条件")
class BarPatternConditionsTest {

    private final BacktestConditionEvaluator evaluator = new BacktestConditionEvaluator();

    @Test
    @DisplayName("一阳穿三阴：阳线覆盖前三阴应命中")
    void yangCoversYinShouldMatch() {
        List<StockDailyData> data = new ArrayList<>();
        // 三根阴线：高开低走
        data.add(bar(10, 12, 9, 9.5, 1_000_000));
        data.add(bar(9.5, 10, 8.5, 8.8, 1_000_000));
        data.add(bar(8.8, 9.2, 8.0, 8.2, 1_000_000));
        // 阳线覆盖三阴高低点
        data.add(bar(8.0, 12.5, 7.8, 12.0, 2_500_000));

        assertTrue(BarPatternConditions.oneYangCoversYin(data, 3, 3));
        assertTrue(evaluator.evaluate(
                Map.of("type", "yang_covers_yin", "yin_count", 3),
                data, 3, Map.of(), false, 0, -1));
        assertTrue(BarPatternConditions.volumeAmplify(data, 3) >= 1.5);
    }

    @Test
    @DisplayName("底部放量：低位 + 连续放量应命中")
    void bottomVolumeShouldMatch() {
        List<StockDailyData> data = new ArrayList<>();
        // 前 58 日高位横盘
        for (int i = 0; i < 58; i++) {
            data.add(bar(100, 101, 99, 100, 1_000_000));
        }
        // 跌至低位并连续两日放量
        data.add(bar(80, 81, 79, 80, 2_500_000));
        data.add(bar(80, 82, 79, 81, 2_600_000));

        int end = data.size() - 1;
        assertTrue(BarPatternConditions.priceNearLow(data, end, 60, 0.25));
        assertTrue(BarPatternConditions.consecutiveVolumeDays(data, end, 2, 2.0, 20));
        assertTrue(evaluator.evaluate(
                Map.of("type", "price_near_low", "lookback", 60, "max_position", 0.25),
                data, end, Map.of(), false, 0, -1));
        assertTrue(evaluator.evaluate(
                Map.of("type", "consecutive_volume_days", "days", 2, "multiple", 2.0, "avg_period", 20),
                data, end, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("动量：涨幅超过阈值应命中")
    void momentumUpShouldMatch() {
        List<StockDailyData> data = List.of(bar(10, 11, 10, 10.5, 1_000_000, 2.0));
        assertTrue(BarPatternConditions.momentumUp(data, 0, 1.5));
        assertTrue(evaluator.evaluate(
                Map.of("type", "momentum_up", "min_change", 1.5),
                data, 0, Map.of(), false, 0, -1));
        assertFalse(BarPatternConditions.momentumUp(data, 0, 3.0));
    }

    @Test
    @DisplayName("振幅低于阈值应命中")
    void amplitudeBelowShouldMatch() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.add(bar(100, 101, 99, 100 + (i % 3) * 0.2, 1_000_000));
        }
        assertTrue(BarPatternConditions.amplitudeBelow(data, 19, 20, 25));
        assertTrue(evaluator.evaluate(
                Map.of("type", "amplitude_below", "lookback", 20, "max_pct", 25),
                data, 19, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("MACD/布林条件在数据不足时返回 false，数据充足时可求值")
    void macdAndBollShouldEvaluateSafely() {
        List<StockDailyData> shortData = List.of(bar(10, 11, 9, 10, 1_000_000));
        assertFalse(BarPatternConditions.macdGoldenCross(shortData, 0));
        assertFalse(BarPatternConditions.bollUpperBreak(shortData, 0, 20, 2.0));

        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double close = 50 + i * 0.5;
            data.add(bar(close, close + 1, close - 1, close, 1_000_000));
        }
        // 末段加速上冲，便于触发上轨突破
        for (int i = 0; i < 5; i++) {
            double close = 70 + i * 3;
            data.add(bar(close, close + 2, close - 1, close, 2_000_000));
        }
        int end = data.size() - 1;
        assertTrue(evaluator.evaluate(Map.of("type", "boll_upper_break", "period", 20, "std_mult", 2.0),
                data, end, Map.of(), false, 0, -1)
                || !BarPatternConditions.bollUpperBreak(data, end, 20, 2.0));
        // 金叉/死叉取决于序列形态，此处只验证可调用且不抛异常
        assertDoesNotThrow(() -> BarPatternConditions.macdGoldenCross(data, end));
        assertDoesNotThrow(() -> evaluator.evaluate(
                Map.of("type", "macd_golden_cross"), data, end, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("通道突破：收盘突破前 N 日高点应命中")
    void channelBreakoutShouldMatch() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.add(bar(100, 101, 99, 100, 1_000_000));
        }
        // 突破前 20 日高点 101
        data.add(bar(101, 105, 100, 104, 2_000_000));
        int end = data.size() - 1;
        assertTrue(BarPatternConditions.channelBreakout(data, end, 20));
        assertTrue(evaluator.evaluate(
                Map.of("type", "channel_breakout", "lookback", 20),
                data, end, Map.of(), false, 0, -1));
    }

    @Test
    @DisplayName("布林下轨触及 + ATR 止损可求值")
    void bollLowerAndAtrStopShouldEvaluate() {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            data.add(bar(100, 101, 99, 100, 1_000_000));
        }
        // 急跌贴近下轨
        data.add(bar(100, 100, 90, 91, 1_500_000));
        int end = data.size() - 1;
        assertDoesNotThrow(() -> BarPatternConditions.bollLowerTouch(data, end, 20, 2.0));
        assertDoesNotThrow(() -> evaluator.evaluate(
                Map.of("type", "boll_lower_touch", "period", 20, "std_mult", 2.0),
                data, end, Map.of(), false, 0, -1));

        double atr = BarPatternConditions.atr(data, end, 20);
        assertFalse(Double.isNaN(atr));
        assertTrue(atr > 0);
        // 入场价远高于现价，ATR 止损应触发
        assertTrue(BarPatternConditions.atrStop(data, end, 20, 2.0, 120));
        assertTrue(evaluator.evaluate(
                Map.of("type", "atr_stop", "period", 20, "multiplier", 2.0),
                data, end, Map.of(), true, 120, end - 1));
    }

    private StockDailyData bar(double open, double high, double low, double close, long volume) {
        return bar(open, high, low, close, volume, null);
    }

    private StockDailyData bar(double open, double high, double low, double close, long volume, Double changePct) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("000001");
        d.setTradeDate(LocalDate.of(2024, 1, 1).plusDays(0));
        d.setOpenPrice(open);
        d.setHighPrice(high);
        d.setLowPrice(low);
        d.setClosePrice(close);
        d.setVolume(volume);
        if (changePct != null) {
            d.setChangePct(changePct);
        } else {
            d.setChangePct(open > 0 ? (close - open) / open * 100 : 0);
        }
        return d;
    }
}
