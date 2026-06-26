package io.leavesfly.stock.application.pipeline;

import io.leavesfly.stock.application.pipeline.AnalysisContextBuilder.AnalysisContext;
import io.leavesfly.stock.application.service.MarketAnalysisService;
import io.leavesfly.stock.application.service.NewsSearchService;
import io.leavesfly.stock.application.strategy.StrategyTestData;
import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.domain.service.TechnicalAnalysisService;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisContextBuilder 分析上下文构建测试")
class AnalysisContextBuilderTest {

    @Mock
    private DataFetcherManager dataFetcher;
    @Mock
    private NewsSearchService newsService;
    @Mock
    private MarketAnalysisService marketService;
    @Mock
    private AppConfig config;

    private AnalysisContextBuilder builder;
    private TechnicalAnalysisService technicalService;

    @BeforeEach
    void setUp() {
        technicalService = new TechnicalAnalysisService();
        builder = new AnalysisContextBuilder(
                dataFetcher, technicalService, newsService, marketService, config);
        when(config.getHistoryDays()).thenReturn(120);
    }

    @Test
    @DisplayName("A 股应组装历史、行情、技术、新闻与大盘环境")
    void buildFullContextForAStock() {
        var history = StrategyTestData.risingBars(30, 100, 0.5);
        when(dataFetcher.getHistoryData(eq("600519"), any(), any())).thenReturn(history);
        when(dataFetcher.getRealtimeQuote("600519")).thenReturn(Map.of("price", 1680.0));
        when(dataFetcher.getStockInfo("600519")).thenReturn(Map.of("industry", "白酒"));
        when(newsService.searchNews("600519", "贵州茅台"))
                .thenReturn(List.of(Map.of("title", "业绩超预期", "content", "详情")));
        when(marketService.getMarketOverview()).thenReturn(Map.of("market_sentiment", "谨慎"));

        AnalysisContext ctx = builder.build("600519");

        assertEquals("600519", ctx.getStockCode());
        assertEquals("贵州茅台", ctx.getStockName());
        assertEquals(MarketType.A, ctx.getMarket());
        assertEquals(30, ctx.getHistoryData().size());
        assertFalse(ctx.getTechnicalAnalysis().isEmpty());
        assertEquals(1, ctx.getNews().size());
        assertEquals("谨慎", ctx.getMarketContext().get("market_sentiment"));
        assertEquals("白酒", ctx.getStockInfo().get("industry"));
    }

    @Test
    @DisplayName("formatForLlm 应输出结构化文本")
    void formatForLlmIncludesSections() {
        var history = StrategyTestData.risingBars(25, 100, 0.5);
        when(dataFetcher.getHistoryData(eq("600519"), any(), any())).thenReturn(history);
        when(dataFetcher.getRealtimeQuote("600519")).thenReturn(Map.of("price", 1680.0));
        when(dataFetcher.getStockInfo("600519")).thenReturn(Map.of());
        when(newsService.searchNews(anyString(), any())).thenReturn(List.of());
        when(marketService.getMarketOverview()).thenReturn(Map.of("index", "上证"));

        AnalysisContext ctx = builder.build("600519");
        String text = builder.formatForLlm(ctx);

        assertTrue(text.contains("## 股票基本信息"));
        assertTrue(text.contains("600519"));
        assertTrue(text.contains("## 实时行情"));
        assertTrue(text.contains("## 近期行情"));
        assertTrue(text.contains("## 技术指标分析"));
        assertTrue(text.contains("## 大盘环境"));
    }
}
