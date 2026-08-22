package io.leavesfly.alphaforge.application.agent.kernel;

import io.leavesfly.alphaforge.application.diagnostics.DiagnosticContext;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisResult;

import java.util.Map;

/**
 * 个股深度分析能力 — 认知轨内核所需的分析能力契约。
 *
 * <p>由内核（消费方）定义、由 {@code AgentAnalysisService}（实现方）实现，
 * 使 {@code agent.kernel} 不再直接依赖 {@code application.service}，消除
 * kernel ↔ service ↔ pipeline 包环，同时保持双轨架构的调用方向：
 * 功能轨（pipeline）与认知轨（kernel）单向向下依赖，不反向穿透。</p>
 */
public interface StockAnalysisCapability {

    /**
     * 执行个股深度分析。
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称（可为 null）
     * @param context   分析上下文（行情、情报、板块等已装配数据）
     * @param diag      诊断上下文，记录各阶段执行轨迹
     * @return 分析结果；失败时可返回 null，由调用方降级处理
     */
    AnalysisResult analyze(String stockCode, String stockName,
                           Map<String, Object> context, DiagnosticContext diag);
}
