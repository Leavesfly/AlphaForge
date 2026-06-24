package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.AnalysisReport;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史记录API控制器 (对齐 dsa-web historyApi)
 * 对应Python版本的 api/v1/endpoints/history.py
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final AnalysisHistoryService historyService;

    public HistoryController(AnalysisHistoryService historyService) {
        this.historyService = historyService;
    }

    /** 分页+筛选获取历史列表 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false, name = "stock_code") String stockCode,
            @RequestParam(required = false, name = "report_type") String reportType,
            @RequestParam(required = false, name = "start_date") String startDate,
            @RequestParam(required = false, name = "end_date") String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        List<AnalysisReport> all = historyService.getRecentReports(stockCode, 1000);
        // 按 report_type 筛选(基于 agentMode 字段)
        if (reportType != null && !reportType.isEmpty()) {
            all = all.stream().filter(r -> reportType.equals(r.getAgentMode())).collect(Collectors.toList());
        }
        // 按日期范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            all = all.stream().filter(r -> r.getAnalysisDate() != null && !r.getAnalysisDate().isBefore(start)).collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
            all = all.stream().filter(r -> r.getAnalysisDate() != null && !r.getAnalysisDate().isAfter(end)).collect(Collectors.toList());
        }
        int total = all.size();
        int start = (page - 1) * limit;
        List<AnalysisReport> items = start < total ? all.subList(start, Math.min(start + limit, total)) : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        result.put("items", items);
        return ResponseEntity.ok(result);
    }

    /** 获取报告详情 */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id) {
        return historyService.getReportById(id)
                .map(r -> ResponseEntity.ok((Object) r))
                .orElse(ResponseEntity.notFound().build());
    }

    /** 获取报告关联新闻 */
    @GetMapping("/{id}/news")
    public ResponseEntity<Map<String, Object>> getNews(@PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        // 简化实现: 从报告中提取新闻信息
        Optional<AnalysisReport> reportOpt = historyService.getReportById(id);
        if (reportOpt.isEmpty()) return ResponseEntity.notFound().build();
        // 返回空新闻列表(实际实现需接入新闻数据源)
        return ResponseEntity.ok(Map.of("total", 0, "items", List.of()));
    }

    /** 获取报告 Markdown 格式内容 */
    @GetMapping("/{id}/markdown")
    public ResponseEntity<Map<String, Object>> getMarkdown(@PathVariable Long id) {
        Optional<AnalysisReport> reportOpt = historyService.getReportById(id);
        if (reportOpt.isEmpty()) return ResponseEntity.notFound().build();
        AnalysisReport report = reportOpt.get();
        // 构建Markdown内容
        StringBuilder md = new StringBuilder();
        md.append("# ").append(report.getStockName() != null ? report.getStockName() : report.getStockCode()).append(" 分析报告\n\n");
        md.append("- 股票代码: ").append(report.getStockCode()).append("\n");
        md.append("- 分析时间: ").append(report.getAnalysisDate()).append("\n");
        md.append("- 综合评分: ").append(report.getTotalScore()).append("\n");
        md.append("- 信号: ").append(report.getSignal()).append("\n\n");
        if (report.getSummary() != null) {
            md.append("## 分析摘要\n\n").append(report.getSummary()).append("\n\n");
        }
        if (report.getLlmResponse() != null) {
            md.append("## AI 分析\n\n").append(report.getLlmResponse()).append("\n");
        }
        return ResponseEntity.ok(Map.of("content", md.toString()));
    }

    /** 获取报告运行诊断摘要 */
    @GetMapping("/{id}/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics(@PathVariable Long id) {
        Optional<AnalysisReport> reportOpt = historyService.getReportById(id);
        if (reportOpt.isEmpty()) return ResponseEntity.notFound().build();
        // 简化实现: 返回基本诊断信息
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("record_id", id);
        diagnostics.put("status", "completed");
        diagnostics.put("total_steps", 5);
        diagnostics.put("completed_steps", 5);
        diagnostics.put("warnings", List.of());
        diagnostics.put("errors", List.of());
        diagnostics.put("duration_ms", 0);
        return ResponseEntity.ok(diagnostics);
    }

    /** 获取报告运行流快照 */
    @GetMapping("/{id}/flow")
    public ResponseEntity<Map<String, Object>> getFlow(@PathVariable Long id) {
        Optional<AnalysisReport> reportOpt = historyService.getReportById(id);
        if (reportOpt.isEmpty()) return ResponseEntity.notFound().build();
        // 简化实现: 返回基本流程快照
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("record_id", id);
        flow.put("status", "completed");
        flow.put("steps", List.of(
            Map.of("name", "数据获取", "status", "completed", "duration_ms", 500),
            Map.of("name", "技术分析", "status", "completed", "duration_ms", 200),
            Map.of("name", "新闻检索", "status", "completed", "duration_ms", 800),
            Map.of("name", "LLM 分析", "status", "completed", "duration_ms", 3000),
            Map.of("name", "报告生成", "status", "completed", "duration_ms", 100)
        ));
        return ResponseEntity.ok(flow);
    }

    /** 获取个股栏列表(不重复个股) */
    @GetMapping("/stocks")
    public ResponseEntity<Map<String, Object>> getStockBarList(
            @RequestParam(required = false, name = "start_date") String startDate,
            @RequestParam(required = false, name = "end_date") String endDate,
            @RequestParam(defaultValue = "50") int limit) {
        List<AnalysisReport> all = historyService.getRecentReports(null, 500);
        // 按日期筛选
        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            all = all.stream().filter(r -> r.getAnalysisDate() != null && !r.getAnalysisDate().isBefore(start)).collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
            all = all.stream().filter(r -> r.getAnalysisDate() != null && !r.getAnalysisDate().isAfter(end)).collect(Collectors.toList());
        }
        // 去重: 每个股票只保留最新一条
        Map<String, AnalysisReport> latestByCode = new LinkedHashMap<>();
        for (AnalysisReport r : all) {
            if (r.getStockCode() != null) {
                latestByCode.putIfAbsent(r.getStockCode(), r);
            }
        }
        List<Map<String, Object>> items = latestByCode.values().stream()
                .limit(limit)
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stock_code", r.getStockCode());
                    item.put("stock_name", r.getStockName());
                    item.put("signal", r.getSignal());
                    item.put("total_score", r.getTotalScore());
                    item.put("current_price", r.getCurrentPrice());
                    item.put("change_pct", r.getChangePct());
                    item.put("analysis_date", r.getAnalysisDate() != null ? r.getAnalysisDate().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("total", items.size(), "items", items));
    }

    /** 统计信息 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_analyses", historyService.getTotalAnalysisCount());
        return ResponseEntity.ok(stats);
    }

    /** 删除单条记录 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        historyService.deleteReport(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    /** 批量删除 */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteRecords(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Number> recordIds = (List<Number>) request.getOrDefault("record_ids", List.of());
        int deleted = 0;
        for (Number id : recordIds) {
            historyService.deleteReport(id.longValue());
            deleted++;
        }
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /** 按股票代码删除 */
    @DeleteMapping("/by-code/{stockCode}")
    public ResponseEntity<Map<String, Object>> deleteByCode(@PathVariable String stockCode) {
        List<AnalysisReport> reports = historyService.getRecentReports(stockCode, 10000);
        int deleted = 0;
        for (AnalysisReport r : reports) {
            if (r.getId() != null) {
                historyService.deleteReport(r.getId());
                deleted++;
            }
        }
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
