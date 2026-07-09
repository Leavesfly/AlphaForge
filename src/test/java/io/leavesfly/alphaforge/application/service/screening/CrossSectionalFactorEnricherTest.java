package io.leavesfly.alphaforge.application.service.screening;

import io.leavesfly.alphaforge.application.strategy.StrategyTestData;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrossSectionalFactorEnricher 截面因子分位")
class CrossSectionalFactorEnricherTest {

    @Mock
    private MarketDataPort marketData;

    @Test
    @DisplayName("应对股票池写入 factor_*_rank 分位")
    void shouldInjectFactorRanks() {
        CrossSectionalFactorEnricher enricher = new CrossSectionalFactorEnricher(marketData);
        StrategyDefinition strategy = StrategyTestData.loadCatalog().find("multi_factor").orElseThrow();

        when(marketData.getHistoryData(eq("AAA"), any(), any())).thenReturn(risingHistory(100, 1.0));
        when(marketData.getHistoryData(eq("BBB"), any(), any())).thenReturn(risingHistory(100, 0.2));
        when(marketData.getHistoryData(eq("CCC"), any(), any())).thenReturn(risingHistory(100, -0.3));

        Map<String, Map<String, Object>> quotes = new LinkedHashMap<>();
        quotes.put("AAA", new LinkedHashMap<>(Map.of("pe", 10)));
        quotes.put("BBB", new LinkedHashMap<>(Map.of("pe", 12)));
        quotes.put("CCC", new LinkedHashMap<>(Map.of("pe", 15)));

        enricher.enrich(strategy, quotes);

        assertTrue(quotes.get("AAA").containsKey("factor_momentum_20_rank"));
        double rankA = ((Number) quotes.get("AAA").get("factor_momentum_20_rank")).doubleValue();
        double rankC = ((Number) quotes.get("CCC").get("factor_momentum_20_rank")).doubleValue();
        assertTrue(rankA > rankC, "强动量应有更高分位: A=" + rankA + " C=" + rankC);
    }

    @Test
    @DisplayName("resolveFactorNames 应从 YAML 解析因子列表")
    void shouldResolveFactorNamesFromYaml() {
        StrategyDefinition multi = StrategyTestData.loadCatalog().find("multi_factor").orElseThrow();
        assertTrue(CrossSectionalFactorEnricher.resolveFactorNames(multi).contains("momentum_20"));
        assertTrue(CrossSectionalFactorEnricher.resolveFactorNames(multi).contains("volatility_20"));

        StrategyDefinition reversal = StrategyTestData.loadCatalog().find("short_reversal").orElseThrow();
        assertTrue(CrossSectionalFactorEnricher.resolveFactorNames(reversal).contains("reversal_5"));
    }

    private List<StockDailyData> risingHistory(double start, double dailyReturn) {
        List<StockDailyData> data = new ArrayList<>();
        double price = start;
        for (int i = 0; i < 40; i++) {
            double next = price * (1 + dailyReturn / 100);
            StockDailyData bar = new StockDailyData();
            bar.setStockCode("X");
            bar.setTradeDate(LocalDate.of(2024, 1, 1).plusDays(i));
            bar.setOpenPrice(price);
            bar.setHighPrice(Math.max(price, next) * 1.01);
            bar.setLowPrice(Math.min(price, next) * 0.99);
            bar.setClosePrice(next);
            bar.setVolume(1_000_000L);
            bar.setChangePct(dailyReturn);
            data.add(bar);
            price = next;
        }
        return data;
    }
}
