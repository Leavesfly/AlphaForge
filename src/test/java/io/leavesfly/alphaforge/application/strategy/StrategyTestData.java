package io.leavesfly.alphaforge.application.strategy;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** 策略相关测试共用的 K 线构造工具 */
public final class StrategyTestData {

    private StrategyTestData() {}

    public static StrategyCatalog loadCatalog() {
        return StrategyCatalogLoader.createAndLoad().getCatalog();
    }

    public static List<StockDailyData> risingBars(int count, double startPrice, double step) {
        return IntStream.range(0, count)
                .mapToObj(i -> bar("600519", "贵州茅台", LocalDate.of(2024, 1, 1).plusDays(i),
                        startPrice + i * step, 1_000_000L + i * 1000L))
                .toList();
    }

    public static StockDailyData bar(String code, String name, LocalDate date, double close, long volume) {
        StockDailyData bar = new StockDailyData();
        bar.setStockCode(code);
        bar.setStockName(name);
        bar.setTradeDate(date);
        bar.setOpenPrice(close - 0.3);
        bar.setClosePrice(close);
        bar.setHighPrice(close + 0.5);
        bar.setLowPrice(close - 0.5);
        bar.setVolume(volume);
        bar.setChangePct(0.5);
        return bar;
    }
}
