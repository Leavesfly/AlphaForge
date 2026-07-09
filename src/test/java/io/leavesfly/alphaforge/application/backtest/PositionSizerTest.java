package io.leavesfly.alphaforge.application.backtest;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PositionSizer ATR 动态仓位")
class PositionSizerTest {

    @Test
    @DisplayName("无 sizing 配置时应返回固定仓位")
    void fixedWhenNoConfig() {
        List<StockDailyData> data = flatBars(30, 100);
        assertEquals(0.8, PositionSizer.resolve(0.8, null, data, 29), 0.001);
        assertEquals(0.8, PositionSizer.resolve(0.8, Map.of(), data, 29), 0.001);
    }

    @Test
    @DisplayName("ATR 模式下高波动应缩小仓位")
    void atrModeShrinksOnHighVolatility() {
        List<StockDailyData> calm = flatBars(30, 100);
        List<StockDailyData> volatileBars = volatileBars(30, 100, 8);

        Map<String, Object> sizing = Map.of(
                "mode", "atr",
                "risk_fraction", 0.01,
                "atr_period", 20,
                "atr_multiplier", 2.0
        );

        double calmPos = PositionSizer.resolve(0.95, sizing, calm, 29);
        double volPos = PositionSizer.resolve(0.95, sizing, volatileBars, 29);

        assertTrue(volPos < calmPos, "calm=" + calmPos + " vol=" + volPos);
        assertTrue(volPos >= 0.01 && volPos <= 0.95);
        assertTrue(calmPos <= 0.95);
    }

    @Test
    @DisplayName("ATR 仓位不应超过 position_size 上限")
    void atrModeRespectsCap() {
        List<StockDailyData> data = flatBars(30, 100);
        Map<String, Object> sizing = Map.of(
                "mode", "atr",
                "risk_fraction", 0.05,
                "atr_period", 20,
                "atr_multiplier", 1.0
        );
        double pos = PositionSizer.resolve(0.5, sizing, data, 29);
        assertTrue(pos <= 0.5 + 1e-9);
    }

    private List<StockDailyData> flatBars(int n, double price) {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            data.add(bar(price, price + 0.5, price - 0.5, price));
        }
        return data;
    }

    private List<StockDailyData> volatileBars(int n, double mid, double range) {
        List<StockDailyData> data = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double close = mid + (i % 2 == 0 ? range : -range);
            data.add(bar(mid, mid + range, mid - range, close));
        }
        return data;
    }

    private StockDailyData bar(double open, double high, double low, double close) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("600519");
        d.setTradeDate(LocalDate.of(2024, 1, 1));
        d.setOpenPrice(open);
        d.setHighPrice(high);
        d.setLowPrice(low);
        d.setClosePrice(close);
        d.setVolume(1_000_000L);
        return d;
    }
}
