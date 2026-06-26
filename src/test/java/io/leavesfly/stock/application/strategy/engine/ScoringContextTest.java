package io.leavesfly.stock.application.strategy.engine;

import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScoringContext 评分上下文测试")
class ScoringContextTest {

    @Test
    @DisplayName("null 映射应回退为空 Map")
    void nullMapsBecomeEmpty() {
        List<StockDailyData> history = StrategyTestData.risingBars(5, 100, 0.5);
        ScoringContext ctx = ScoringContext.of(history, null, null, null);

        assertTrue(ctx.getTechnical().isEmpty());
        assertTrue(ctx.getQuote().isEmpty());
        assertTrue(ctx.getMarketContext().isEmpty());
    }

    @Test
    @DisplayName("应正确读取 K 线字段")
    void barAccessors() {
        StockDailyData bar = StrategyTestData.bar("600519", "贵州茅台",
                java.time.LocalDate.of(2024, 1, 1), 105.0, 2_000_000L);
        ScoringContext ctx = ScoringContext.of(List.of(bar), Map.of(), Map.of(), Map.of());

        assertEquals(1, ctx.size());
        assertEquals(105.0, ctx.close(0));
        assertEquals(2_000_000L, ctx.volume(0));
        assertEquals(0.5, ctx.changePct(0));
    }
}
