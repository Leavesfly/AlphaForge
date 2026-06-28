package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术指标分析工具
 *
 * 基于历史K线数据计算MA、MACD、RSI、KDJ等技术指标
 */
@Component
public class TechnicalAnalysisTool implements Tool {

    private final MarketDataPort dataFetcher;
    private final TechnicalAnalysisService technicalService;

    public TechnicalAnalysisTool(MarketDataPort dataFetcher,
                                  TechnicalAnalysisService technicalService) {
        this.dataFetcher = dataFetcher;
        this.technicalService = technicalService;
    }

    @Override
    public String name() {
        return "technical_analysis";
    }

    @Override
    public String description() {
        return "对指定股票进行技术指标分析，包括MA均线、MACD、RSI、KDJ等指标计算和趋势判断";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> stockCode = new HashMap<>();
        stockCode.put("type", "string");
        stockCode.put("description", "股票代码，如 600519、000001");
        properties.put("stock_code", stockCode);

        params.put("properties", properties);
        params.put("required", new String[]{"stock_code"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String code = (String) args.get("stock_code");
        if (code == null || code.isBlank()) {
            throw new ToolException("参数 stock_code 不能为空", "PARAM_MISSING");
        }

        List<StockDailyData> data = dataFetcher.getHistoryData(code,
                LocalDate.now().minusDays(60), LocalDate.now());
        if (data == null || data.isEmpty()) {
            return "数据不足，无法进行技术分析";
        }

        Map<String, Object> result = technicalService.analyze(data);
        return result != null ? result.toString() : "技术分析结果为空";
    }
}
