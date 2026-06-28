package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalogLoader;
import io.leavesfly.alphaforge.application.strategy.engine.StrategyPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrategyController 策略 API 测试")
class StrategyControllerTest {

    private StrategyCatalog catalog;
    private StrategyController controller;

    @BeforeEach
    void setUp() {
        var loader = StrategyCatalogLoader.createAndLoad();
        catalog = loader.getCatalog();
        controller = new StrategyController(catalog, loader, new StrategyPerformanceTracker());
    }

    @Test
    @DisplayName("应返回全部策略列表")
    @SuppressWarnings("unchecked")
    void listAllStrategies() {
        ResponseEntity<Map<String, Object>> response = controller.list(null);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(19, body.get("total"));
        List<Map<String, Object>> strategies = (List<Map<String, Object>>) body.get("strategies");
        assertEquals(19, strategies.size());
        assertTrue(strategies.stream().anyMatch(s -> "ma_golden_cross".equals(s.get("id"))));
    }

    @Test
    @DisplayName("应按 capability 过滤回测策略")
    @SuppressWarnings("unchecked")
    void listBacktestStrategiesOnly() {
        ResponseEntity<Map<String, Object>> response = controller.list("backtest");

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        List<Map<String, Object>> strategies = (List<Map<String, Object>>) body.get("strategies");
        assertTrue(strategies.size() >= 10);
        strategies.forEach(s ->
                assertTrue(((List<String>) s.get("capabilities")).contains("backtest")));
    }

    @Test
    @DisplayName("应按 capability 过滤选股策略")
    @SuppressWarnings("unchecked")
    void listScreeningStrategiesOnly() {
        ResponseEntity<Map<String, Object>> response = controller.list("screening");

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(4, body.get("total"));
    }
}
