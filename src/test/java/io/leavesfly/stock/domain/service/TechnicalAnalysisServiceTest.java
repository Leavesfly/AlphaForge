package io.leavesfly.stock.domain.service;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TechnicalAnalysisService 技术分析服务测试")
class TechnicalAnalysisServiceTest {

    private TechnicalAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new TechnicalAnalysisService();
    }

    /**
     * 构造测试用的K线数据
     */
    private List<StockDailyData> createTestData(int count, double basePrice) {
        List<StockDailyData> data = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < count; i++) {
            StockDailyData d = new StockDailyData();
            d.setStockCode("600519");
            d.setTradeDate(date.plusDays(i));
            double close = basePrice + i * 0.5;
            d.setOpenPrice(close - 0.3);
            d.setHighPrice(close + 0.5);
            d.setLowPrice(close - 0.5);
            d.setClosePrice(close);
            d.setVolume(1000000L + i * 10000L);
            d.setAmount(close * 1000000);
            data.add(d);
        }
        return data;
    }

    @Nested
    @DisplayName("analyze - 完整技术分析")
    class AnalyzeTests {

        @Test
        @DisplayName("数据不足返回error")
        void insufficientDataReturnsError() {
            List<StockDailyData> data = createTestData(3, 10.0);
            Map<String, Object> result = service.analyze(data);
            assertNotNull(result);
            assertEquals("数据不足，无法进行技术分析", result.get("error"));
        }

        @Test
        @DisplayName("null数据返回error")
        void nullDataReturnsError() {
            Map<String, Object> result = service.analyze(null);
            assertNotNull(result);
            assertEquals("数据不足，无法进行技术分析", result.get("error"));
        }

        @Test
        @DisplayName("5条数据能完成基本分析")
        void fiveRecordsCompletesAnalysis() {
            List<StockDailyData> data = createTestData(5, 10.0);
            Map<String, Object> result = service.analyze(data);
            assertNotNull(result);
            assertTrue(result.containsKey("ma_analysis"));
            assertTrue(result.containsKey("total_score"));
        }

        @Test
        @DisplayName("60条数据完成全指标分析")
        void sixtyRecordsFullAnalysis() {
            List<StockDailyData> data = createTestData(60, 100.0);
            Map<String, Object> result = service.analyze(data);
            assertNotNull(result);

            // 检查所有指标都存在
            assertTrue(result.containsKey("ma_analysis"));
            assertTrue(result.containsKey("macd"));
            assertTrue(result.containsKey("kdj"));
            assertTrue(result.containsKey("rsi"));
            assertTrue(result.containsKey("boll"));
            assertTrue(result.containsKey("volume_analysis"));
            assertTrue(result.containsKey("trend"));
            assertTrue(result.containsKey("total_score"));

            // 检查综合评分在0-100范围内
            int score = (int) result.get("total_score");
            assertTrue(score >= 0 && score <= 100, "score should be 0-100, got " + score);
        }

        @Test
        @DisplayName("上涨趋势数据应触发强势分析")
        void uptrendDataTriggersBullishAnalysis() {
            List<StockDailyData> data = createTestData(30, 10.0);
            Map<String, Object> result = service.analyze(data);
            assertNotNull(result);
            String trend = (String) result.get("trend");
            assertNotNull(trend);
            // 持续上涨应判定为强势上涨
            assertEquals("强势上涨", trend);
        }

        @Test
        @DisplayName("下跌趋势数据应触发弱势分析")
        void downtrendDataTriggersBearishAnalysis() {
            List<StockDailyData> data = new ArrayList<>();
            LocalDate date = LocalDate.of(2024, 1, 1);
            for (int i = 0; i < 30; i++) {
                StockDailyData d = new StockDailyData();
                d.setStockCode("600519");
                d.setTradeDate(date.plusDays(i));
                double close = 50.0 - i * 0.5;
                d.setOpenPrice(close + 0.3);
                d.setHighPrice(close + 0.5);
                d.setLowPrice(close - 0.5);
                d.setClosePrice(close);
                d.setVolume(1000000L);
                data.add(d);
            }
            Map<String, Object> result = service.analyze(data);
            assertNotNull(result);
            String trend = (String) result.get("trend");
            assertEquals("弱势下跌", trend);
        }
    }

    @Nested
    @DisplayName("MA分析验证")
    class MaAnalysisTests {

        @Test
        @DisplayName("30条数据应包含MA5/MA10/MA20/MA30")
        void thirtyRecordsIncludeMultipleMA() {
            List<StockDailyData> data = createTestData(30, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> ma = (Map<String, Object>) result.get("ma_analysis");
            assertNotNull(ma);
            assertTrue(ma.containsKey("MA5"));
            assertTrue(ma.containsKey("MA10"));
            assertTrue(ma.containsKey("MA20"));
            assertTrue(ma.containsKey("MA30"));
        }

        @Test
        @DisplayName("60条数据应包含MA60")
        void sixtyRecordsIncludeMA60() {
            List<StockDailyData> data = createTestData(60, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> ma = (Map<String, Object>) result.get("ma_analysis");
            assertNotNull(ma);
            assertTrue(ma.containsKey("MA60"));
        }

        @Test
        @DisplayName("均线排列判断")
        void maArrangementDetected() {
            List<StockDailyData> data = createTestData(30, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> ma = (Map<String, Object>) result.get("ma_analysis");
            assertNotNull(ma);
            String arrangement = (String) ma.get("arrangement");
            assertNotNull(arrangement);
            assertTrue(arrangement.equals("多头排列") || arrangement.equals("空头排列") || arrangement.equals("交叉纠缠"));
        }
    }

    @Nested
    @DisplayName("MACD分析验证")
    class MacdAnalysisTests {

        @Test
        @DisplayName("少于26条数据MACD状态为数据不足")
        void lessThan26RecordsMacdInsufficient() {
            List<StockDailyData> data = createTestData(20, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> macd = (Map<String, Object>) result.get("macd");
            assertNotNull(macd);
            assertEquals("数据不足", macd.get("status"));
        }

        @Test
        @DisplayName("60条数据MACD应有DIF/DEA/MACD值")
        void sixtyRecordsMacdHasValues() {
            List<StockDailyData> data = createTestData(60, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> macd = (Map<String, Object>) result.get("macd");
            assertNotNull(macd);
            assertNotNull(macd.get("DIF"));
            assertNotNull(macd.get("DEA"));
            assertNotNull(macd.get("MACD"));
        }
    }

    @Nested
    @DisplayName("量能分析验证")
    class VolumeAnalysisTests {

        @Test
        @DisplayName("量能分析包含volume_ratio")
        void volumeAnalysisContainsRatio() {
            List<StockDailyData> data = createTestData(30, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> vol = (Map<String, Object>) result.get("volume_analysis");
            assertNotNull(vol);
            assertNotNull(vol.get("volume_ratio"));
        }

        @Test
        @DisplayName("量能分析包含status判断")
        void volumeAnalysisContainsStatus() {
            List<StockDailyData> data = createTestData(30, 10.0);
            Map<String, Object> result = service.analyze(data);
            @SuppressWarnings("unchecked")
            Map<String, Object> vol = (Map<String, Object>) result.get("volume_analysis");
            assertNotNull(vol);
            String status = (String) vol.get("status");
            assertNotNull(status);
        }
    }
}
