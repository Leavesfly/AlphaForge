package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.domain.model.entity.StockDailyData;
import io.leavesfly.stock.domain.model.enums.MarketType;
import io.leavesfly.stock.domain.service.TradingCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据质量校验器
 *
 * 校验获取的行情数据完整性与合理性：
 * 1. 缺失交易日检测 — 对比交易日历，找出缺失的交易日
 * 2. 异常价格检测 — 零/负价格、极端涨跌幅
 * 3. OHLC 一致性校验 — high >= max(open, close) >= min(open, close) >= low
 * 4. 成交量合理性 — 负成交量检测
 */
@Component
public class DataQualityValidator {

    private static final Logger log = LoggerFactory.getLogger(DataQualityValidator.class);

    /** A股涨跌幅上限（普通股票10%，科创板/创业板20%，取宽松值22%） */
    private static final double A_SHARE_DAILY_LIMIT = 22.0;
    /** 港美股无涨跌限制，但单日50%以上波动视为异常 */
    private static final double GLOBAL_DAILY_LIMIT = 50.0;
    /** 价格上限（万元） */
    private static final double MAX_PRICE = 100000.0;

    private final TradingCalendar tradingCalendar;

    public DataQualityValidator(TradingCalendar tradingCalendar) {
        this.tradingCalendar = tradingCalendar;
    }

    /**
     * 校验结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> issues;
        private final List<LocalDate> missingDates;

        public ValidationResult(boolean valid, List<String> issues, List<LocalDate> missingDates) {
            this.valid = valid;
            this.issues = issues;
            this.missingDates = missingDates;
        }

        public boolean isValid() { return valid; }
        public List<String> getIssues() { return issues; }
        public List<LocalDate> getMissingDates() { return missingDates; }

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult withIssues(List<String> issues, List<LocalDate> missingDates) {
            return new ValidationResult(issues.isEmpty(), issues, missingDates);
        }
    }

    /**
     * 执行完整校验
     *
     * @param data      行情数据列表（按日期升序）
     * @param stockCode 股票代码（用于判断市场类型）
     * @return 校验结果
     */
    public ValidationResult validate(List<StockDailyData> data, String stockCode) {
        if (data == null || data.isEmpty()) {
            return new ValidationResult(false, List.of("数据为空"), List.of());
        }

        List<String> issues = new ArrayList<>();
        List<LocalDate> missingDates = new ArrayList<>();

        // 1. 缺失交易日检测
        if (tradingCalendar != null) {
            missingDates = detectMissingTradingDays(data, stockCode);
            if (!missingDates.isEmpty()) {
                issues.add(String.format("缺失 %d 个交易日: %s",
                        missingDates.size(),
                        missingDates.size() <= 5 ? missingDates : missingDates.subList(0, 5) + "..."));
            }
        }

        // 2. 逐条数据校验
        MarketType market = MarketType.detectFromCode(stockCode);
        double dailyLimit = (market == MarketType.A) ? A_SHARE_DAILY_LIMIT : GLOBAL_DAILY_LIMIT;

        for (int i = 0; i < data.size(); i++) {
            StockDailyData bar = data.get(i);

            // 价格零/负值检测
            if (bar.getClosePrice() == null || bar.getClosePrice() <= 0) {
                issues.add(String.format("[%s] 收盘价异常: %s", bar.getTradeDate(), bar.getClosePrice()));
                continue;
            }
            if (bar.getOpenPrice() != null && bar.getOpenPrice() <= 0) {
                issues.add(String.format("[%s] 开盘价异常: %s", bar.getTradeDate(), bar.getOpenPrice()));
            }

            // 价格上限检测
            if (bar.getClosePrice() > MAX_PRICE) {
                issues.add(String.format("[%s] 收盘价超出合理范围: %.2f", bar.getTradeDate(), bar.getClosePrice()));
            }

            // OHLC 一致性校验
            if (bar.getHighPrice() != null && bar.getLowPrice() != null &&
                bar.getOpenPrice() != null && bar.getClosePrice() != null) {
                double maxOC = Math.max(bar.getOpenPrice(), bar.getClosePrice());
                double minOC = Math.min(bar.getOpenPrice(), bar.getClosePrice());
                if (bar.getHighPrice() < maxOC) {
                    issues.add(String.format("[%s] 最高价 %.2f < max(开,收) %.2f",
                            bar.getTradeDate(), bar.getHighPrice(), maxOC));
                }
                if (bar.getLowPrice() > minOC) {
                    issues.add(String.format("[%s] 最低价 %.2f > min(开,收) %.2f",
                            bar.getTradeDate(), bar.getLowPrice(), minOC));
                }
                if (bar.getHighPrice() < bar.getLowPrice()) {
                    issues.add(String.format("[%s] 最高价 %.2f < 最低价 %.2f",
                            bar.getTradeDate(), bar.getHighPrice(), bar.getLowPrice()));
                }
            }

            // 涨跌幅异常检测（需要有前一日收盘价）
            if (i > 0) {
                StockDailyData prev = data.get(i - 1);
                if (prev.getClosePrice() != null && prev.getClosePrice() > 0 &&
                    bar.getClosePrice() != null) {
                    double changePct = Math.abs(
                            (bar.getClosePrice() - prev.getClosePrice()) / prev.getClosePrice() * 100);
                    if (changePct > dailyLimit) {
                        issues.add(String.format("[%s] 涨跌幅异常: %.2f%% (前收:%.2f 今收:%.2f)",
                                bar.getTradeDate(), changePct, prev.getClosePrice(), bar.getClosePrice()));
                    }
                }
            }

            // 成交量负值检测
            if (bar.getVolume() != null && bar.getVolume() < 0) {
                issues.add(String.format("[%s] 成交量为负: %d", bar.getTradeDate(), bar.getVolume()));
            }
        }

        if (!issues.isEmpty()) {
            log.warn("[{}] 数据质量校验发现 {} 个问题 (数据量:{}条)",
                    stockCode, issues.size(), data.size());
            // 只打印前5条
            for (int i = 0; i < Math.min(5, issues.size()); i++) {
                log.warn("  - {}", issues.get(i));
            }
        }

        return ValidationResult.withIssues(issues, missingDates);
    }

    /**
     * 检测缺失的交易日
     */
    private List<LocalDate> detectMissingTradingDays(List<StockDailyData> data, String stockCode) {
        if (data.size() < 2) return List.of();

        LocalDate start = data.get(0).getTradeDate();
        LocalDate end = data.get(data.size() - 1).getTradeDate();

        // 收集已有数据的日期集合
        Set<LocalDate> existingDates = data.stream()
                .map(StockDailyData::getTradeDate)
                .collect(Collectors.toSet());

        List<LocalDate> missing = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            if (tradingCalendar.isTradingDay(cursor) && !existingDates.contains(cursor)) {
                missing.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return missing;
    }
}
