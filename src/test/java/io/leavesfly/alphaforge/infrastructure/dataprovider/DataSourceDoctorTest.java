package io.leavesfly.alphaforge.infrastructure.dataprovider;

import io.leavesfly.alphaforge.config.DataSourceDoctorConfig;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.model.enums.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DataSourceDoctor 数据源主动体检测试")
class DataSourceDoctorTest {

    @Mock
    private BaseDataFetcher fetcher;

    private DataSourceDoctor doctor;

    @BeforeEach
    void setUp() {
        DataSourceDoctorConfig config = new DataSourceDoctorConfig(null); // 不调 init()，用默认探针/TTL
        doctor = new DataSourceDoctor(List.of(fetcher), config);
        when(fetcher.getName()).thenReturn("mock");
        when(fetcher.getDataFamily()).thenReturn("mock");
        when(fetcher.getPriority()).thenReturn(0);
        when(fetcher.isAvailable()).thenReturn(true);
        when(fetcher.getSupportedMarkets()).thenReturn(Set.of(MarketType.A));
    }

    private StockDailyData bar(LocalDate date, double close) {
        StockDailyData d = new StockDailyData();
        d.setTradeDate(date);
        d.setClosePrice(close);
        return d;
    }

    // ===== 错误分类归因 =====

    @Test
    @DisplayName("错误分类：429/限流关键词归 RATE_LIMIT")
    void classifyRateLimit() {
        assertEquals(DataSourceDoctor.ISSUE_RATE_LIMIT,
                DataSourceDoctor.classify(new RuntimeException("HTTP 429 Too Many Requests")));
        assertEquals(DataSourceDoctor.ISSUE_RATE_LIMIT,
                DataSourceDoctor.classify(new RuntimeException("请求过于频繁，被限流")));
    }

    @Test
    @DisplayName("错误分类：401/token 归 NO_KEY")
    void classifyNoKey() {
        assertEquals(DataSourceDoctor.ISSUE_NO_KEY,
                DataSourceDoctor.classify(new RuntimeException("HTTP 401 Unauthorized")));
        assertEquals(DataSourceDoctor.ISSUE_NO_KEY,
                DataSourceDoctor.classify(new RuntimeException("invalid api key token")));
    }

    @Test
    @DisplayName("错误分类：timeout/connect/dns 归 NETWORK")
    void classifyNetwork() {
        assertEquals(DataSourceDoctor.ISSUE_NETWORK,
                DataSourceDoctor.classify(new RuntimeException("connect timed out")));
        assertEquals(DataSourceDoctor.ISSUE_NETWORK,
                DataSourceDoctor.classify(new RuntimeException("UnknownHost: dns resolve failed")));
    }

    @Test
    @DisplayName("错误分类：其余归 DATA_ERROR；环境类 issueType=environment、数据类=code")
    void classifyDataErrorAndIssueType() {
        assertEquals(DataSourceDoctor.ISSUE_DATA_ERROR,
                DataSourceDoctor.classify(new RuntimeException("JSON 解析失败")));
        assertEquals("environment", DataSourceDoctor.issueTypeOf(DataSourceDoctor.ISSUE_RATE_LIMIT));
        assertEquals("environment", DataSourceDoctor.issueTypeOf(DataSourceDoctor.ISSUE_NO_KEY));
        assertEquals("environment", DataSourceDoctor.issueTypeOf(DataSourceDoctor.ISSUE_NETWORK));
        assertEquals("environment", DataSourceDoctor.issueTypeOf("unsupported"));
        assertEquals("none", DataSourceDoctor.issueTypeOf("ok"));
        assertEquals("code", DataSourceDoctor.issueTypeOf(DataSourceDoctor.ISSUE_DATA_ERROR));
    }

    // ===== 探测语义 =====

    @Test
    @DisplayName("成功探测：记录耗时语义/末根K线日期/K线数，A 股探针 600519")
    void probeSuccessRecordsLastBar() {
        LocalDate last = LocalDate.now().minusDays(1);
        when(fetcher.getHistoryData(eq("600519"), any(), any()))
                .thenReturn(List.of(bar(last.minusDays(1), 10), bar(last, 11)));

        Map<String, Object> report = doctor.probeAll();
        List<?> fetchers = (List<?>) report.get("fetchers");
        Map<?, ?> item = (Map<?, ?>) fetchers.get(0);

        assertEquals("ok", item.get("status"));
        assertEquals(last.toString(), item.get("lastBarDate"));
        assertEquals(2, item.get("bars"));
        assertEquals("600519", item.get("probeSymbol"));
        Map<?, ?> summary = (Map<?, ?>) report.get("summary");
        assertEquals(1, summary.get("ok"));
        assertEquals(0, summary.get("failed"));
    }

    @Test
    @DisplayName("不可用源归 NO_KEY 环境问题（缺 Key 非代码问题）")
    void unavailableFetcherIsNoKey() {
        when(fetcher.isAvailable()).thenReturn(false);

        Map<?, ?> item = (Map<?, ?>) ((List<?>) doctor.probeAll().get("fetchers")).get(0);
        assertEquals(DataSourceDoctor.ISSUE_NO_KEY, item.get("status"));
        assertEquals("environment", item.get("issueType"));
    }

    @Test
    @DisplayName("抛限流异常归 RATE_LIMIT 环境问题")
    void rateLimitExceptionClassified() {
        when(fetcher.getHistoryData(anyString(), any(), any()))
                .thenThrow(new RuntimeException("HTTP 429 too many requests"));

        Map<?, ?> item = (Map<?, ?>) ((List<?>) doctor.probeAll().get("fetchers")).get(0);
        assertEquals(DataSourceDoctor.ISSUE_RATE_LIMIT, item.get("status"));
        assertEquals("environment", item.get("issueType"));
    }

    @Test
    @DisplayName("返回空数据归 DATA_ERROR 疑似代码问题")
    void emptyBarsIsDataError() {
        when(fetcher.getHistoryData(anyString(), any(), any())).thenReturn(List.of());

        Map<?, ?> item = (Map<?, ?>) ((List<?>) doctor.probeAll().get("fetchers")).get(0);
        assertEquals(DataSourceDoctor.ISSUE_DATA_ERROR, item.get("status"));
        assertEquals("code", item.get("issueType"));
    }

    // ===== TTL 缓存 / force 旁路 =====

    @Test
    @DisplayName("TTL 内二次请求命中缓存：不再真实拉取（0 网络）")
    void ttlCacheHitAvoidsRefetch() {
        when(fetcher.getHistoryData(anyString(), any(), any()))
                .thenReturn(List.of(bar(LocalDate.now(), 10)));

        Map<String, Object> first = doctor.getCachedOrProbe(false);
        assertEquals(false, first.get("cached"));

        Map<String, Object> second = doctor.getCachedOrProbe(false);
        assertEquals(true, second.get("cached"));
        // 第二次命中缓存：真实拉取仅发生一次
        verify(fetcher, times(1)).getHistoryData(anyString(), any(), any());
    }

    @Test
    @DisplayName("force=true 绕过 TTL 重新探测")
    void forceBypassesCache() {
        when(fetcher.getHistoryData(anyString(), any(), any()))
                .thenReturn(List.of(bar(LocalDate.now(), 10)));

        doctor.getCachedOrProbe(false);
        Map<String, Object> forced = doctor.getCachedOrProbe(true);
        assertEquals(false, forced.get("cached"));
        verify(fetcher, times(2)).getHistoryData(anyString(), any(), any());
    }

    @Test
    @DisplayName("报告契约：checkedAt/ttlSeconds/summary 键就位")
    void reportContract() {
        when(fetcher.getHistoryData(anyString(), any(), any()))
                .thenReturn(List.of(bar(LocalDate.now(), 10)));

        Map<String, Object> report = doctor.getCachedOrProbe(true);
        assertTrue(report.containsKey("checkedAt"));
        assertEquals(300, report.get("ttlSeconds"));
        assertTrue(report.get("summary") instanceof Map);
        assertTrue(report.get("fetchers") instanceof List);
    }
}

