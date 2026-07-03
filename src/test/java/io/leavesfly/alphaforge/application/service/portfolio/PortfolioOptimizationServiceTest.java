package io.leavesfly.alphaforge.application.service.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.repository.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.domain.service.portfolio.PortfolioOptimizer;
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

@DisplayName("组合优化应用服务 PortfolioOptimizationService")
class PortfolioOptimizationServiceTest {

    private MarketDataPort marketData;
    private PortfolioOptimizationService service;

    @BeforeEach
    void setUp() {
        marketData = mock(MarketDataPort.class);
        PortfolioRepository repo = mock(PortfolioRepository.class);
        service = new PortfolioOptimizationService(marketData, repo, new PortfolioOptimizer());
    }

    /** 生成 60 个交易日、给定日漂移与波动的合成序列 */
    private List<StockDailyData> series(String code, double drift, double vol, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        List<StockDailyData> list = new ArrayList<>();
        double price = 100;
        LocalDate d = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 60; i++) {
            price *= (1 + drift + vol * rnd.nextGaussian());
            StockDailyData bar = new StockDailyData();
            bar.setStockCode(code);
            bar.setTradeDate(d.plusDays(i));
            bar.setClosePrice(price);
            list.add(bar);
        }
        return list;
    }

    @Test
    @DisplayName("两标的：权重和为1并给出建仓股数")
    void optimizeTwoStocks() {
        when(marketData.getHistoryData(eq("A"), any(), any())).thenReturn(series("A", 0.001, 0.01, 1));
        when(marketData.getHistoryData(eq("B"), any(), any())).thenReturn(series("B", 0.0008, 0.02, 2));

        Map<String, Object> result = service.optimize(List.of("A", "B"), "max_sharpe",
                180, 100000, 3.0, 0.02);

        assertNull(result.get("error"), "不应报错: " + result.get("error"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) result.get("allocations");
        assertEquals(2, allocations.size());
        double sum = allocations.stream().mapToDouble(a -> ((Number) a.get("weight")).doubleValue()).sum();
        assertEquals(1.0, sum, 1e-6);
        assertTrue(allocations.get(0).containsKey("suggested_shares"));
    }

    @Test
    @DisplayName("标的不足2个：返回错误")
    void tooFewSymbols() {
        Map<String, Object> result = service.optimize(List.of("A"), "min_variance", 180, 0, 3.0, 0.02);
        assertNotNull(result.get("error"));
    }

    @Test
    @DisplayName("历史数据缺失：剔除后不足则报错")
    void missingData() {
        when(marketData.getHistoryData(eq("A"), any(), any())).thenReturn(series("A", 0.001, 0.01, 1));
        when(marketData.getHistoryData(eq("B"), any(), any())).thenReturn(List.of());
        Map<String, Object> result = service.optimize(List.of("A", "B"), "max_sharpe", 180, 0, 3.0, 0.02);
        assertNotNull(result.get("error"));
    }
}
