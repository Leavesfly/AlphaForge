package io.leavesfly.alphaforge.application.service.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.alphaforge.infrastructure.persistence.portfolio.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioService 投资组合服务测试")
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepo;

    @Mock
    private DataFetcherManager dataFetcher;

    @InjectMocks
    private PortfolioService service;

    private PortfolioPosition createPosition(Long id, String code, int qty, double cost) {
        PortfolioPosition p = new PortfolioPosition();
        p.setId(id);
        p.setStockCode(code);
        p.setStockName("测试股票" + code);
        p.setQuantity(qty);
        p.setCostPrice(cost);
        p.setMarketValue(cost * qty);
        return p;
    }

    @Nested
    @DisplayName("getAllPositions - 获取所有持仓")
    class GetAllPositionsTests {

        @Test
        @DisplayName("返回所有持仓列表")
        void returnsAllPositions() {
            List<PortfolioPosition> positions = Arrays.asList(
                createPosition(1L, "600519", 100, 1800.0),
                createPosition(2L, "000001", 200, 15.0)
            );
            when(portfolioRepo.findAllByOrderByUpdatedAtDesc()).thenReturn(positions);

            List<PortfolioPosition> result = service.getAllPositions();
            assertEquals(2, result.size());
            assertEquals("600519", result.get(0).getStockCode());
        }

        @Test
        @DisplayName("空持仓返回空列表")
        void emptyPositionsReturnsEmptyList() {
            when(portfolioRepo.findAllByOrderByUpdatedAtDesc()).thenReturn(Collections.emptyList());
            List<PortfolioPosition> result = service.getAllPositions();
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("addPosition - 添加持仓")
    class AddPositionTests {

        @Test
        @DisplayName("新持仓直接保存")
        void newPositionSavedDirectly() {
            PortfolioPosition position = createPosition(null, "600519", 100, 1800.0);
            when(portfolioRepo.findByStockCode("600519")).thenReturn(Optional.empty());
            when(portfolioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioPosition result = service.addPosition(position);
            assertNotNull(result);
            verify(portfolioRepo).save(position);
        }

        @Test
        @DisplayName("已存在持仓进行加权平均合并")
        void existingPositionMerged() {
            PortfolioPosition existing = createPosition(1L, "600519", 100, 1800.0);
            PortfolioPosition newPos = createPosition(null, "600519", 100, 1900.0);

            when(portfolioRepo.findByStockCode("600519")).thenReturn(Optional.of(existing));
            when(portfolioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioPosition result = service.addPosition(newPos);
            assertNotNull(result);
            assertEquals(200, result.getQuantity());
            // 加权平均: (1800*100 + 1900*100) / 200 = 1850
            assertEquals(1850.0, result.getCostPrice(), 0.01);
        }
    }

    @Nested
    @DisplayName("reducePosition - 减仓")
    class ReducePositionTests {

        @Test
        @DisplayName("部分减仓更新数量")
        void partialReduceUpdatesQuantity() {
            PortfolioPosition existing = createPosition(1L, "600519", 200, 1800.0);
            when(portfolioRepo.findByStockCode("600519")).thenReturn(Optional.of(existing));
            when(portfolioRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioPosition result = service.reducePosition("600519", 50);
            assertNotNull(result);
            assertEquals(150, result.getQuantity());
        }

        @Test
        @DisplayName("全部减仓删除持仓")
        void fullReduceDeletesPosition() {
            PortfolioPosition existing = createPosition(1L, "600519", 100, 1800.0);
            when(portfolioRepo.findByStockCode("600519")).thenReturn(Optional.of(existing));

            PortfolioPosition result = service.reducePosition("600519", 100);
            assertNull(result);
            verify(portfolioRepo).delete(existing);
        }

        @Test
        @DisplayName("不存在的持仓返回null")
        void nonExistentReturnsNull() {
            when(portfolioRepo.findByStockCode("999999")).thenReturn(Optional.empty());
            PortfolioPosition result = service.reducePosition("999999", 10);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("deletePosition - 删除持仓")
    class DeletePositionTests {

        @Test
        @DisplayName("按ID删除持仓")
        void deleteById() {
            service.deletePosition(1L);
            verify(portfolioRepo).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("getPortfolioSummary - 投资组合概要")
    class GetPortfolioSummaryTests {

        @Test
        @DisplayName("空持仓返回零值概要")
        void emptyPositionsReturnsZeroSummary() {
            when(portfolioRepo.findAll()).thenReturn(Collections.emptyList());
            Map<String, Object> summary = service.getPortfolioSummary();
            assertNotNull(summary);
            assertEquals(0, summary.get("total_positions"));
            assertEquals(0L, summary.get("profit_count"));
            assertEquals(0L, summary.get("loss_count"));
            assertEquals(0.0, summary.get("win_rate_pct"));
        }

        @Test
        @DisplayName("多个持仓计算正确的概要数据")
        void multiplePositionsSummary() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            p1.setMarketValue(200000.0);
            p1.setProfitLoss(20000.0);
            p1.setProfitLossPct(11.1);

            PortfolioPosition p2 = createPosition(2L, "000001", 200, 15.0);
            p2.setMarketValue(2800.0);
            p2.setProfitLoss(-200.0);
            p2.setProfitLossPct(-6.7);

            when(portfolioRepo.findAll()).thenReturn(Arrays.asList(p1, p2));
            Map<String, Object> summary = service.getPortfolioSummary();
            assertNotNull(summary);
            assertEquals(2, summary.get("total_positions"));
            assertEquals(202800.0, (Double) summary.get("total_market_value"), 0.01);
            assertEquals(19800.0, (Double) summary.get("total_profit_loss"), 0.01);
            assertEquals(1L, summary.get("profit_count"));
            assertEquals(1L, summary.get("loss_count"));
            assertEquals(50.0, (Double) summary.get("win_rate_pct"), 0.01);
        }
    }

    @Nested
    @DisplayName("assessRisk - 风险评估")
    class AssessRiskTests {

        @Test
        @DisplayName("空持仓风险等级为medium")
        void emptyPositionsRiskMedium() {
            when(portfolioRepo.findAll()).thenReturn(Collections.emptyList());
            Map<String, Object> risk = service.assessRisk();
            assertNotNull(risk);
            assertEquals(0.0, (Double) risk.get("max_concentration_pct"), 0.01);
            assertEquals("medium", risk.get("overall_risk_level"));
        }

        @Test
        @DisplayName("高集中度风险等级为high")
        void highConcentrationRiskHigh() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            p1.setPositionPct(50.0);
            p1.setProfitLossPct(5.0);

            when(portfolioRepo.findAll()).thenReturn(Collections.singletonList(p1));
            Map<String, Object> risk = service.assessRisk();
            assertNotNull(risk);
            assertEquals("高", risk.get("concentration_risk"));
            assertEquals("high", risk.get("overall_risk_level"));
        }

        @Test
        @DisplayName("中等集中度风险等级为中")
        void mediumConcentrationRiskMedium() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            p1.setPositionPct(25.0);
            p1.setProfitLossPct(5.0);

            when(portfolioRepo.findAll()).thenReturn(Collections.singletonList(p1));
            Map<String, Object> risk = service.assessRisk();
            assertNotNull(risk);
            assertEquals("中", risk.get("concentration_risk"));
            assertEquals("medium", risk.get("overall_risk_level"));
        }

        @Test
        @DisplayName("低集中度风险等级为低")
        void lowConcentrationRiskLow() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            p1.setPositionPct(10.0);
            p1.setProfitLossPct(5.0);

            when(portfolioRepo.findAll()).thenReturn(Collections.singletonList(p1));
            Map<String, Object> risk = service.assessRisk();
            assertNotNull(risk);
            assertEquals("低", risk.get("concentration_risk"));
        }

        @Test
        @DisplayName("深度亏损持仓数量统计")
        void deepLossCountCalculated() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            p1.setPositionPct(50.0);
            p1.setProfitLossPct(-15.0); // 深度亏损

            PortfolioPosition p2 = createPosition(2L, "000001", 200, 15.0);
            p2.setPositionPct(50.0);
            p2.setProfitLossPct(5.0); // 盈利

            when(portfolioRepo.findAll()).thenReturn(Arrays.asList(p1, p2));
            Map<String, Object> risk = service.assessRisk();
            assertNotNull(risk);
            assertEquals(1L, risk.get("deep_loss_count"));
            assertEquals("high", risk.get("overall_risk_level"));
        }
    }

    @Nested
    @DisplayName("refreshPositions - 刷新持仓")
    class RefreshPositionsTests {

        @Test
        @DisplayName("刷新持仓更新价格和盈亏")
        void refreshUpdatesPriceAndProfitLoss() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            when(portfolioRepo.findAll()).thenReturn(Collections.singletonList(p1));
            Map<String, Object> quote = new HashMap<>();
            quote.put("current_price", 1900.0);
            when(dataFetcher.getRealtimeQuote("600519")).thenReturn(quote);

            service.refreshPositions();

            assertEquals(1900.0, p1.getCurrentPrice());
            assertEquals(190000.0, p1.getMarketValue());
            assertEquals(10000.0, p1.getProfitLoss());
            assertEquals(100.0, p1.getPositionPct());
        }

        @Test
        @DisplayName("数据获取失败不抛异常")
        void fetchFailureDoesNotThrow() {
            PortfolioPosition p1 = createPosition(1L, "600519", 100, 1800.0);
            when(portfolioRepo.findAll()).thenReturn(Collections.singletonList(p1));
            when(dataFetcher.getRealtimeQuote("600519")).thenThrow(new RuntimeException("network error"));

            assertDoesNotThrow(() -> service.refreshPositions());
        }
    }
}
