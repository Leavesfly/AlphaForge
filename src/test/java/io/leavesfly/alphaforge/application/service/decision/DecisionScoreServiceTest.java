package io.leavesfly.alphaforge.application.service.decision;

import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import io.leavesfly.alphaforge.config.DecisionConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.repository.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.domain.service.decision.DecisionTestBars;
import io.leavesfly.alphaforge.domain.service.decision.LightsResult;
import io.leavesfly.alphaforge.domain.service.decision.Verdict;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionScoreService 编排测试 — K 线/基准/画像乘数/持仓联动/lotSize/市场上下文")
class DecisionScoreServiceTest {

    private static final String CODE = "600519";
    private static final String BENCH = "000300";

    @Mock
    private MarketDataPort marketDataPort;

    @Mock
    private UserRiskProfileService profileService;

    @Mock
    private PortfolioRepository portfolioRepository;

    private DecisionScoreService service;

    @BeforeEach
    void setUp() {
        // 不调用 init()：沿用 DecisionConfig 字段默认值（500 天窗口/000300 基准/仓位上限 1.0）
        service = new DecisionScoreService(marketDataPort, profileService,
                new DecisionConfig(new EnvVarProvider()), portfolioRepository);
    }

    private void stubProfile(double multiplier, Double capitalWan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("riskTolerance", "BALANCED");
        view.put("positionMultiplier", multiplier);
        view.put("capitalAmount", capitalWan);
        when(profileService.getProfileView()).thenReturn(view);
    }

    private void stubAshare(List<StockDailyData> stock, List<StockDailyData> bench) {
        when(marketDataPort.getHistoryData(eq(CODE), any(), any())).thenReturn(stock);
        when(marketDataPort.getHistoryData(eq(BENCH), any(), any())).thenReturn(bench);
    }

    private PortfolioPosition heldPosition(String code, int qty, double cost) {
        PortfolioPosition p = new PortfolioPosition();
        p.setStockCode(code);
        p.setQuantity(qty);
        p.setCostPrice(cost);
        return p;
    }

    @Test
    @DisplayName("A 股完整编排：上涨序列+基准 → 趋势买点，资金万元转元，上下文含基准")
    void aShareHappyPath() {
        stubAshare(DecisionTestBars.alternating(300, 100, 0.008, -0.004),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict(),
                "实际 " + result.getVerdict() + " / " + result.lightsSummary());
        assertNotNull(result.getPlan());
        assertNotNull(result.getPlan().getSizing());
        assertTrue(result.getPlan().getSizing().getSuggestedShares() > 0, "10 万资金应给出建议股数");
        assertEquals(100_000.0, result.getPlan().getSizing().getCapital(), 1e-9, "10 万元 → 100000 元");
        assertEquals(100, result.getPlan().getSizing().getLotSize(), "A 股一手 100 股");

        Map<String, Object> context = result.getMarketContext();
        assertEquals("A", context.get("market"));
        assertEquals(BENCH, context.get("benchmarkCode"));
        assertEquals("沪深300", context.get("benchmarkName"));
    }

    @Test
    @DisplayName("代码为空/空白：抛 IllegalArgumentException")
    void blankCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.score(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.score("  ", null));
    }

    @Test
    @DisplayName("K 线不足 MIN_BARS：诚实降级 unrated 三灰灯")
    void insufficientBarsIsUnrated() {
        stubAshare(DecisionTestBars.series(200, 100, 0.01),
                DecisionTestBars.series(200, 4000, 0.0));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.UNRATED, result.getVerdict());
        assertEquals(200, result.getNBars());
    }

    @Test
    @DisplayName("cost 显式传入：进入持仓联动视角")
    void costTriggersPositionView() {
        stubAshare(DecisionTestBars.alternating(300, 100, 0.008, -0.004),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score(CODE, 80.0);

        assertNotNull(result.getPosition());
        assertEquals(80.0, (Double) result.getPosition().get("cost"), 1e-9);
    }

    @Test
    @DisplayName("基准拉取失败：相对强度降级（权重并入动量），结论照常输出")
    void benchmarkFailureDegrades() {
        when(marketDataPort.getHistoryData(eq(CODE), any(), any()))
                .thenReturn(DecisionTestBars.alternating(300, 100, 0.008, -0.004));
        when(marketDataPort.getHistoryData(eq(BENCH), any(), any()))
                .thenThrow(new RuntimeException("rate limited"));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict(),
                "无基准时趋势分按 90/10 权重，仍应为趋势买点，实际 " + result.getVerdict());
        // 基准失败不影响上下文声明（仍标注意图使用的基准）
        assertEquals(BENCH, result.getMarketContext().get("benchmarkCode"));
    }

    @Test
    @DisplayName("美股代码：不拉基准指数，lotSize=1")
    void usStockSkipsBenchmark() {
        when(marketDataPort.getHistoryData(eq("AAPL"), any(), any()))
                .thenReturn(DecisionTestBars.alternating(300, 150, 0.008, -0.004));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score("AAPL", null);

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict());
        assertEquals("US", result.getMarketContext().get("market"));
        assertNull(result.getMarketContext().get("benchmarkCode"));
        assertEquals(1, result.getPlan().getSizing().getLotSize());
        verify(marketDataPort, times(1)).getHistoryData(any(), any(LocalDate.class), any());
    }

    @Test
    @DisplayName("画像资金未填：计划保留但资金预算法不附加 sizing")
    void nullCapitalOmitsSizing() {
        stubAshare(DecisionTestBars.alternating(300, 100, 0.008, -0.004),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, null);

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict());
        assertNotNull(result.getPlan());
        assertNull(result.getPlan().getSizing(), "资金缺失时不应给出建议股数");
    }

    @Test
    @DisplayName("无 cost 时自动取登记持仓：势红 → 持仓需减风险，source=portfolio")
    void heldPositionAutoInjectsPortfolioView() {
        stubAshare(DecisionTestBars.series(300, 100, -0.01),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, 10.0);
        when(portfolioRepository.findByStockCode(CODE))
                .thenReturn(java.util.Optional.of(heldPosition(CODE, 1000, 150.0)));

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.REDUCE_RISK, result.getVerdict(), "势红+持仓 → 持仓需减风险");
        assertEquals(150.0, (Double) result.getPosition().get("cost"), 1e-9);
        assertEquals(1000.0, (Double) result.getPosition().get("shares"), 1e-9);
        assertEquals("portfolio", result.getPosition().get("source"));
    }

    @Test
    @DisplayName("显式 cost 触发手动视角：source=manual（不取登记持仓）")
    void explicitCostOverridesHeldPosition() {
        stubAshare(DecisionTestBars.alternating(300, 100, 0.008, -0.004),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, 10.0);

        LightsResult result = service.score(CODE, 80.0);

        assertNotNull(result.getPosition());
        assertEquals(80.0, (Double) result.getPosition().get("cost"), 1e-9, "显式 cost 生效");
        assertEquals("manual", result.getPosition().get("source"));
        verify(portfolioRepository, times(0)).findByStockCode(CODE);
    }

    @Test
    @DisplayName("持仓查询失败：忽略联动不影响评估输出")
    void portfolioFailureDegradesGracefully() {
        stubAshare(DecisionTestBars.alternating(300, 100, 0.008, -0.004),
                DecisionTestBars.series(300, 4000, 0.0));
        stubProfile(1.0, 10.0);
        when(portfolioRepository.findByStockCode(CODE))
                .thenThrow(new RuntimeException("db down"));

        LightsResult result = service.score(CODE, null);

        assertEquals(Verdict.TREND_ENTRY, result.getVerdict());
        assertNull(result.getPosition(), "持仓查询失败时不应有持仓视角");
    }
}
