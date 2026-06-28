package io.leavesfly.stock.application.service.screening;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.leavesfly.stock.domain.model.entity.backtest.BacktestRecord;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.port.MarketDataPort;
import io.leavesfly.stock.infrastructure.persistence.backtest.BacktestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将回测记录转换为前端图表可用的可视化数据。
 */
@Service
public class BacktestVisualizationService {

    private static final Logger log = LoggerFactory.getLogger(BacktestVisualizationService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BacktestRepository backtestRepo;
    private final MarketDataPort dataFetcher;
    private final ObjectMapper objectMapper;

    public BacktestVisualizationService(BacktestRepository backtestRepo, MarketDataPort dataFetcher) {
        this.backtestRepo = backtestRepo;
        this.dataFetcher = dataFetcher;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Optional<Map<String, Object>> buildVisualization(long recordId) {
        BacktestRecord record = backtestRepo.findById(recordId);
        if (record == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> equityCurve = loadEquityCurve(record);
        if (equityCurve.isEmpty()) {
            equityCurve = rebuildEquityCurve(record);
        }

        List<Map<String, Object>> trades = parseJsonList(record.getTradeDetails());
        Map<String, Object> diagnostics = parseJsonMap(record.getDiagnostics());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_id", record.getId());
        payload.put("stock_code", record.getStockCode());
        payload.put("stock_name", record.getStockName());
        payload.put("strategy_name", record.getStrategyName());
        payload.put("start_date", record.getStartDate());
        payload.put("end_date", record.getEndDate());
        payload.put("summary", buildSummary(record, diagnostics));
        payload.put("equity_curve", buildEquitySeries(equityCurve));
        payload.put("trades", normalizeTrades(trades));
        payload.put("trade_markers", buildTradeMarkers(trades, equityCurve));
        payload.put("monthly_returns", buildMonthlyReturns(equityCurve));
        payload.put("diagnostics", diagnostics);
        return Optional.of(payload);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEquityCurve(BacktestRecord record) {
        Map<String, Object> diagnostics = parseJsonMap(record.getDiagnostics());
        Object raw = diagnostics.get("equity_curve");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(item -> (Map<String, Object>) objectMapper.convertValue(item, Map.class)).toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> rebuildEquityCurve(BacktestRecord record) {
        if (record.getStartDate() == null || record.getEndDate() == null || record.getStockCode() == null) {
            return List.of();
        }
        LocalDate start = record.getStartDate().toLocalDate();
        LocalDate end = record.getEndDate().toLocalDate();
        List<StockDailyData> bars = dataFetcher.getHistoryData(record.getStockCode(), start, end);
        if (bars.isEmpty()) {
            return List.of();
        }

        double initialCapital = record.getInitialCapital() != null ? record.getInitialCapital() : 100_000;
        double benchmarkShares = initialCapital / bars.get(0).getClosePrice();
        List<Map<String, Object>> trades = new ArrayList<>(parseJsonList(record.getTradeDetails()));
        trades.sort((a, b) -> stringDate(a).compareTo(stringDate(b)));

        double cash = initialCapital;
        int shares = 0;
        double peak = initialCapital;
        List<Map<String, Object>> curve = new ArrayList<>();
        int tradeIndex = 0;

        for (StockDailyData bar : bars) {
            while (tradeIndex < trades.size()) {
                Map<String, Object> trade = trades.get(tradeIndex);
                String tradeDate = stringDate(trade);
                if (tradeDate.isEmpty()) {
                    tradeIndex++;
                    continue;
                }
                int cmp = tradeDate.compareTo(bar.getTradeDate().toString());
                if (cmp > 0) {
                    break;
                }
                if (cmp == 0) {
                    String side = String.valueOf(trade.get("side"));
                    int tradeShares = intValue(trade.get("shares"));
                    double price = doubleValue(trade.get("price"));
                    double commission = doubleValue(trade.get("commission"));
                    double stampTax = doubleValue(firstPresent(trade, "stampTax", "stamp_tax"));
                    if ("buy".equalsIgnoreCase(side)) {
                        cash -= tradeShares * price + commission;
                        shares += tradeShares;
                    } else if ("sell".equalsIgnoreCase(side)) {
                        cash += tradeShares * price - commission - stampTax;
                        shares -= tradeShares;
                    }
                }
                tradeIndex++;
            }

            double close = bar.getClosePrice();
            double portfolioValue = cash + shares * close;
            if (portfolioValue > peak) {
                peak = portfolioValue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", bar.getTradeDate().toString());
            point.put("portfolioValue", portfolioValue);
            point.put("benchmarkValue", benchmarkShares * close);
            point.put("drawdownPct", peak > 0 ? (peak - portfolioValue) / peak * 100 : 0);
            point.put("closePrice", close);
            curve.add(point);
        }
        return curve;
    }

    private Map<String, Object> buildSummary(BacktestRecord record, Map<String, Object> diagnostics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("initial_capital", record.getInitialCapital());
        summary.put("final_capital", record.getFinalCapital());
        summary.put("total_return_pct", record.getTotalReturnPct());
        summary.put("annual_return_pct", record.getAnnualReturnPct());
        summary.put("max_drawdown_pct", record.getMaxDrawdownPct());
        summary.put("sharpe_ratio", record.getSharpeRatio());
        summary.put("win_rate_pct", record.getWinRatePct());
        summary.put("total_trades", record.getTotalTrades());
        summary.put("benchmark_return_pct", record.getBenchmarkReturnPct());
        summary.put("alpha_pct", record.getAlphaPct());
        summary.put("profit_loss_ratio", record.getProfitLossRatio());
        summary.put("avg_holding_days", record.getAvgHoldingDays());
        if (diagnostics != null) {
            summary.put("total_commission", diagnostics.get("total_commission"));
            summary.put("total_stamp_tax", diagnostics.get("total_stamp_tax"));
            summary.put("total_slippage_cost", diagnostics.get("total_slippage_cost"));
        }
        return summary;
    }

    private Map<String, Object> buildEquitySeries(List<Map<String, Object>> equityCurve) {
        List<String> dates = new ArrayList<>();
        List<Double> portfolioValues = new ArrayList<>();
        List<Double> benchmarkValues = new ArrayList<>();
        List<Double> drawdownPct = new ArrayList<>();
        List<Double> closePrices = new ArrayList<>();

        for (Map<String, Object> point : equityCurve) {
            dates.add(stringDate(point));
            portfolioValues.add(doubleValue(firstPresent(point, "portfolioValue", "portfolio_value")));
            benchmarkValues.add(doubleValue(firstPresent(point, "benchmarkValue", "benchmark_value")));
            drawdownPct.add(doubleValue(firstPresent(point, "drawdownPct", "drawdown_pct")));
            closePrices.add(doubleValue(firstPresent(point, "closePrice", "close_price")));
        }

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("dates", dates);
        series.put("portfolio_values", portfolioValues);
        series.put("benchmark_values", benchmarkValues);
        series.put("drawdown_pct", drawdownPct);
        series.put("close_prices", closePrices);
        return series;
    }

    private List<Map<String, Object>> normalizeTrades(List<Map<String, Object>> trades) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> trade : trades) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", stringDate(trade));
            row.put("side", trade.get("side"));
            row.put("price", doubleValue(trade.get("price")));
            row.put("shares", intValue(trade.get("shares")));
            row.put("amount", doubleValue(trade.get("amount")));
            row.put("commission", doubleValue(trade.get("commission")));
            row.put("stamp_tax", doubleValue(firstPresent(trade, "stampTax", "stamp_tax")));
            row.put("reason", trade.get("reason"));
            normalized.add(row);
        }
        return normalized;
    }

    private List<Map<String, Object>> buildTradeMarkers(List<Map<String, Object>> trades,
                                                        List<Map<String, Object>> equityCurve) {
        if (trades.isEmpty() || equityCurve.isEmpty()) {
            return List.of();
        }
        Map<String, Double> priceByDate = new LinkedHashMap<>();
        for (Map<String, Object> point : equityCurve) {
            priceByDate.put(stringDate(point), doubleValue(firstPresent(point, "closePrice", "close_price")));
        }

        List<Map<String, Object>> markers = new ArrayList<>();
        for (Map<String, Object> trade : trades) {
            String date = stringDate(trade);
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("date", date);
            marker.put("side", trade.get("side"));
            marker.put("price", doubleValue(trade.get("price")));
            marker.put("shares", intValue(trade.get("shares")));
            marker.put("close_price", priceByDate.getOrDefault(date, doubleValue(trade.get("price"))));
            markers.add(marker);
        }
        return markers;
    }

    private List<Map<String, Object>> buildMonthlyReturns(List<Map<String, Object>> equityCurve) {
        if (equityCurve.size() < 2) {
            return List.of();
        }
        Map<YearMonth, Double> monthStart = new LinkedHashMap<>();
        Map<YearMonth, Double> monthEnd = new LinkedHashMap<>();

        for (Map<String, Object> point : equityCurve) {
            String dateText = stringDate(point);
            if (dateText.length() < 7) {
                continue;
            }
            YearMonth month = YearMonth.parse(dateText.substring(0, 7));
            double value = doubleValue(firstPresent(point, "portfolioValue", "portfolio_value"));
            monthStart.putIfAbsent(month, value);
            monthEnd.put(month, value);
        }

        List<Map<String, Object>> monthly = new ArrayList<>();
        for (Map.Entry<YearMonth, Double> entry : monthEnd.entrySet()) {
            YearMonth month = entry.getKey();
            double start = monthStart.getOrDefault(month, entry.getValue());
            double end = entry.getValue();
            double returnPct = start > 0 ? (end - start) / start * 100 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month.format(MONTH_FMT));
            row.put("return_pct", returnPct);
            monthly.add(row);
        }
        return monthly;
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析回测 JSON 列表失败", e);
            return List.of();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析回测 JSON 对象失败", e);
            return Map.of();
        }
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String stringDate(Map<String, Object> map) {
        Object value = firstPresent(map, "date", "tradeDate", "trade_date");
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            return String.format("%04d-%02d-%02d",
                    ((Number) list.get(0)).intValue(),
                    ((Number) list.get(1)).intValue(),
                    ((Number) list.get(2)).intValue());
        }
        String text = String.valueOf(value);
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
