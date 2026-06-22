package com.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 运行诊断服务
 * 对应Python: run_diagnostics.py + notification_diagnostics.py
 * 收集分析流程每步的执行状态、耗时、异常
 */
@Service
public class RunDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(RunDiagnosticsService.class);
    private final List<Map<String, Object>> recentRuns = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY = 100;

    /** 记录一次运行诊断 */
    public void recordRun(String runId, String stockCode, Map<String, Object> diagnostics) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("run_id", runId);
        entry.put("stock_code", stockCode);
        entry.put("timestamp", System.currentTimeMillis());
        entry.putAll(diagnostics);
        recentRuns.add(entry);
        if (recentRuns.size() > MAX_HISTORY) recentRuns.remove(0);
    }

    /** 获取最近N次运行诊断 */
    public List<Map<String, Object>> getRecentRuns(int count) {
        int start = Math.max(0, recentRuns.size() - count);
        return new ArrayList<>(recentRuns.subList(start, recentRuns.size()));
    }

    /** 获取运行健康摘要 */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_runs", recentRuns.size());
        long failures = recentRuns.stream().filter(r -> "failed".equals(r.get("status"))).count();
        summary.put("failures", failures);
        summary.put("success_rate", recentRuns.isEmpty() ? 100.0 : (1 - (double) failures / recentRuns.size()) * 100);
        return summary;
    }
}
