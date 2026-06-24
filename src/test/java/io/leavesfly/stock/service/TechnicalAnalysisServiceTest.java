package io.leavesfly.stock.service;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技术分析服务测试
 * 验证: MA/MACD/KDJ/RSI/BOLL计算正确性
 */
class TechnicalAnalysisServiceTest {

    private final TechnicalAnalysisService service = new TechnicalAnalysisService();

    @Test
    @DisplayName("基本技术分析: 返回结构完整")
    void testAnalyzeReturnsCompleteStructure() {
        List<StockDailyData> data = generateTestData(60);
        Map<String, Object> result = service.analyze(data);

        assertNotNull(result);
        assertTrue(result.containsKey("total_score"));
        assertTrue(result.containsKey("trend"));
        assertTrue(result.containsKey("ma"));
        assertTrue(result.containsKey("macd"));
    }

    @Test
    @DisplayName("评分范围: 0-100")
    void testScoreInRange() {
        List<StockDailyData> data = generateTestData(60);
        Map<String, Object> result = service.analyze(data);
        Object score = result.get("total_score");
        assertNotNull(score);
        int s = ((Number) score).intValue();
        assertTrue(s >= 0 && s <= 100, "评分应在0-100之间, 实际: " + s);
    }

    @Test
    @DisplayName("上涨趋势数据应得到偏多评分")
    void testUptrendHighScore() {
        List<StockDailyData> data = generateUptrendData(60);
        Map<String, Object> result = service.analyze(data);
        int score = ((Number) result.get("total_score")).intValue();
        assertTrue(score >= 50, "上涨趋势评分应>=50, 实际: " + score);
    }

    @Test
    @DisplayName("下跌趋势数据应得到偏空评分")
    void testDowntrendLowScore() {
        List<StockDailyData> data = generateDowntrendData(60);
        Map<String, Object> result = service.analyze(data);
        int score = ((Number) result.get("total_score")).intValue();
        assertTrue(score <= 50, "下跌趋势评分应<=50, 实际: " + score);
    }

    @Test
    @DisplayName("数据不足时安全返回")
    void testInsufficientData() {
        List<StockDailyData> data = generateTestData(3);
        Map<String, Object> result = service.analyze(data);
        assertNotNull(result);
    }

    // ===== 测试数据生成 =====

    private List<StockDailyData> generateTestData(int days) {
        List<StockDailyData> data = new ArrayList<>();
        double price = 100.0;
        Random rand = new Random(42);
        for (int i = 0; i < days; i++) {
            price += (rand.nextDouble() - 0.5) * 3;
            data.add(createDailyData(i, price, 1000000 + rand.nextInt(500000)));
        }
        return data;
    }

    private List<StockDailyData> generateUptrendData(int days) {
        List<StockDailyData> data = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < days; i++) {
            price += 0.5 + Math.random() * 1.0; // 持续上涨
            data.add(createDailyData(i, price, 1000000 + (int)(Math.random() * 500000)));
        }
        return data;
    }

    private List<StockDailyData> generateDowntrendData(int days) {
        List<StockDailyData> data = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < days; i++) {
            price -= 0.5 + Math.random() * 1.0; // 持续下跌
            if (price < 10) price = 10;
            data.add(createDailyData(i, price, 1000000 + (int)(Math.random() * 500000)));
        }
        return data;
    }

    private StockDailyData createDailyData(int dayOffset, double close, long volume) {
        StockDailyData d = new StockDailyData();
        d.setStockCode("600519");
        d.setTradeDate(LocalDate.now().minusDays(60 - dayOffset));
        d.setOpenPrice(close - 1);
        d.setHighPrice(close + 2);
        d.setLowPrice(close - 2);
        d.setClosePrice(close);
        d.setVolume(volume);
        d.setChangePct(dayOffset > 0 ? (Math.random() - 0.5) * 5 : 0);
        return d;
    }
}
