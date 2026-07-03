package io.leavesfly.alphaforge.application.factor.analysis;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.factor.ClassicFactorLibrary;
import io.leavesfly.alphaforge.domain.service.factor.FactorLayerAnalyzer;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("经典因子分析服务 FactorAnalysisService")
class FactorAnalysisServiceTest {

    private MarketDataPort marketData;
    private FactorAnalysisService service;

    @BeforeEach
    void setUp() {
        marketData = mock(MarketDataPort.class);
        service = new FactorAnalysisService(marketData, new ClassicFactorLibrary(), new FactorLayerAnalyzer());
    }

    private List<StockDailyData> trend(String code, double dailyDrift) {
        List<StockDailyData> list = new ArrayList<>();
        double price = 50;
        LocalDate d = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 120; i++) {
            price *= (1 + dailyDrift);
            StockDailyData bar = new StockDailyData();
            bar.setStockCode(code);
            bar.setTradeDate(d.plusDays(i));
            bar.setClosePrice(price);
            bar.setVolume(1_000_000L);
            bar.setAmount(price * 1_000_000);
            bar.setTurnoverRate(2.0);
            list.add(bar);
        }
        return list;
    }

    @Test
    @DisplayName("列出内置因子非空")
    void availableFactors() {
        assertFalse(service.availableFactors().isEmpty());
    }

    @Test
    @DisplayName("动量因子：不同漂移股票池可完成分层分析")
    void analyzeMomentum() {
        List<String> codes = List.of("A", "B", "C", "D", "E");
        double[] drifts = {-0.002, -0.001, 0.0, 0.001, 0.002};
        for (int i = 0; i < codes.size(); i++) {
            when(marketData.getHistoryData(eq(codes.get(i)), any(), any()))
                    .thenReturn(trend(codes.get(i), drifts[i]));
        }

        Map<String, Object> result = service.analyze(codes, "momentum_20", 200, 5, 5);

        assertNull(result.get("error"), "不应报错: " + result.get("error"));
        assertEquals("momentum_20", result.get("factor"));
        assertTrue(result.containsKey("ic_mean"));
        assertTrue(result.containsKey("long_short_return"));
        assertTrue(result.containsKey("interpretation"));
    }

    @Test
    @DisplayName("不支持的因子：返回错误")
    void unsupportedFactor() {
        Map<String, Object> result = service.analyze(List.of("A", "B", "C", "D", "E"),
                "not_a_factor", 200, 5, 5);
        assertNotNull(result.get("error"));
    }

    @Test
    @DisplayName("股票池小于分层数：返回错误")
    void tooSmallUniverse() {
        Map<String, Object> result = service.analyze(List.of("A", "B"), "momentum_20", 200, 5, 5);
        assertNotNull(result.get("error"));
    }
}
