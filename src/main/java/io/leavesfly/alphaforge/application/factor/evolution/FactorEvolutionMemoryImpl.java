package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.application.factor.evolution.model.FactorEvolutionRecord;
import io.leavesfly.alphaforge.application.factor.evolution.model.FailurePattern;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorGenerationContext;
import io.leavesfly.alphaforge.application.factor.evolution.model.FactorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 因子进化记忆实现 — 内存存储 + 失败模式提取
 *
 * 采用与现有 ExperienceMemory 相似的内存存储策略（ConcurrentHashMap），
 * 后续可通过 Repository + MyBatis 持久化到 SQLite（已建表）。
 */
@Service
public class FactorEvolutionMemoryImpl implements FactorEvolutionMemory {

    private static final Logger log = LoggerFactory.getLogger(FactorEvolutionMemoryImpl.class);

    private static final int MAX_RECORDS = 2000;
    private static final int MAX_TOP_PERFORMERS = 50;

    /** 全局进化记录（按时间排序） */
    private final CopyOnWriteArrayList<FactorEvolutionRecord> allRecords = new CopyOnWriteArrayList<>();

    /** 因子 ID → 进化记录列表（谱系树） */
    private final Map<String, CopyOnWriteArrayList<FactorEvolutionRecord>> factorHistory = new ConcurrentHashMap<>();

    /** 失败模式缓存 */
    private volatile List<FailurePattern> cachedFailurePatterns = null;

    /** 当前进化代数 */
    private volatile int currentGeneration = 0;

    /** 每代的最佳 IC 记录（用于收敛判断） */
    private final List<Double> bestICPerGeneration = new CopyOnWriteArrayList<>();

    @Override
    public void recordEvolution(FactorEvolutionRecord record) {
        if (record == null) return;

        allRecords.add(record);
        while (allRecords.size() > MAX_RECORDS) {
            allRecords.remove(0);
        }

        factorHistory.computeIfAbsent(record.getFactorId(), k -> new CopyOnWriteArrayList<>())
                .add(record);

        if (record.getGenerationRound() > currentGeneration) {
            currentGeneration = record.getGenerationRound();
        }

        // 记录每代最优 IC
        if (record.isSuccessful()) {
            while (bestICPerGeneration.size() <= record.getGenerationRound()) {
                bestICPerGeneration.add(0.0);
            }
            if (record.getIc() > bestICPerGeneration.get(record.getGenerationRound())) {
                bestICPerGeneration.set(record.getGenerationRound(), record.getIc());
            }
        }

        // 失效缓存
        cachedFailurePatterns = null;

        log.debug("记录因子进化: {} gen={} status={} ic={:.4f}",
                record.getFactorName(), record.getGenerationRound(),
                record.getStatus(), record.getIc());
    }

    @Override
    public List<FactorEvolutionRecord> getEvolutionHistory(String factorId) {
        CopyOnWriteArrayList<FactorEvolutionRecord> list = factorHistory.get(factorId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public List<FactorEvolutionRecord> getTopPerformers(int limit) {
        return allRecords.stream()
                .filter(FactorEvolutionRecord::isSuccessful)
                .sorted(Comparator.comparingDouble(FactorEvolutionRecord::getEvaluationScore).reversed())
                .limit(Math.min(limit, MAX_TOP_PERFORMERS))
                .collect(Collectors.toList());
    }

    @Override
    public List<FactorEvolutionRecord> getTopPerformersByCondition(String marketCondition, int limit) {
        return allRecords.stream()
                .filter(r -> r.isSuccessful() && marketCondition.equals(r.getMarketCondition()))
                .sorted(Comparator.comparingDouble(FactorEvolutionRecord::getEvaluationScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<FailurePattern> getFailurePatterns() {
        if (cachedFailurePatterns != null) return cachedFailurePatterns;

        // 从淘汰因子中提取模式
        Map<String, List<FactorEvolutionRecord>> patternsByCategory = allRecords.stream()
                .filter(FactorEvolutionRecord::isFailed)
                .collect(Collectors.groupingBy(r ->
                        r.getCategory() + "|" + r.getMarketCondition()));

        List<FailurePattern> patterns = new ArrayList<>();
        for (Map.Entry<String, List<FactorEvolutionRecord>> entry : patternsByCategory.entrySet()) {
            List<FactorEvolutionRecord> failed = entry.getValue();
            if (failed.size() < 2) continue;

            String[] parts = entry.getKey().split("\\|", 2);
            String category = parts[0];
            String condition = parts.length > 1 ? parts[1] : "any";

            double avgIC = failed.stream().mapToDouble(FactorEvolutionRecord::getIc).average().orElse(0);
            double avgScore = failed.stream().mapToDouble(FactorEvolutionRecord::getEvaluationScore).average().orElse(0);

            String description = String.format("在 %s 市况下，%s 类因子平均 IC=%.4f，失败 %d 次",
                    condition, category, avgIC, failed.size());

            patterns.add(new FailurePattern(
                    entry.getKey(),
                    failed.size(),
                    avgIC,
                    avgScore,
                    description,
                    category
            ));
        }

        patterns.sort(Comparator.comparingInt(FailurePattern::getOccurrenceCount).reversed());
        cachedFailurePatterns = patterns;
        return patterns;
    }

    @Override
    public List<FactorEvolutionRecord> getFailedFactorsByCategory(String category) {
        return allRecords.stream()
                .filter(r -> r.isFailed() && category.equals(r.getCategory()))
                .collect(Collectors.toList());
    }

    @Override
    public String buildEvolutionHint(FactorGenerationContext context) {
        if (allRecords.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n## 因子进化经验记忆\n");

        // 成功因子 Top 3
        List<FactorEvolutionRecord> top = getTopPerformers(3);
        if (!top.isEmpty()) {
            sb.append("### 历史最优因子（供变异参考）\n");
            for (FactorEvolutionRecord r : top) {
                sb.append(String.format("- %s: expr=%s IC=%.4f IR=%.4f Sharpe=%.2f 代数=%d\n",
                        r.getFactorName(), r.getFactorExpression(),
                        r.getIc(), r.getIr(), r.getSharpeRatio(), r.getGenerationRound()));
            }
        }

        // 失败模式 Top 3
        List<FailurePattern> failures = getFailurePatterns();
        if (!failures.isEmpty()) {
            sb.append("\n### 失败模式（需避免）\n");
            failures.stream().limit(3).forEach(f ->
                    sb.append(String.format("- %s（出现%d次）\n", f.getFailureDescription(), f.getOccurrenceCount())));
        }

        // 按市场条件推荐
        if (context.getMarketPhase() != null) {
            List<FactorEvolutionRecord> condTop = getTopPerformersByCondition(context.getMarketPhase(), 3);
            if (!condTop.isEmpty()) {
                sb.append(String.format("\n### 在 %s 市况下表现最优的因子\n", context.getMarketPhase()));
                for (FactorEvolutionRecord r : condTop) {
                    sb.append(String.format("- %s: IC=%.4f Score=%.1f\n", r.getFactorName(), r.getIc(), r.getEvaluationScore()));
                }
            }
        }

        return sb.toString();
    }

    @Override
    public int getCurrentGeneration() {
        return currentGeneration;
    }

    @Override
    public boolean isConverged(int minGenerations, double threshold) {
        if (bestICPerGeneration.size() < minGenerations + 1) return false;

        // 检查最近 minGenerations 代的 IC 提升是否都小于阈值
        int start = bestICPerGeneration.size() - minGenerations;
        for (int i = start; i < bestICPerGeneration.size(); i++) {
            double prev = bestICPerGeneration.get(i - 1);
            double curr = bestICPerGeneration.get(i);
            if (curr - prev > threshold) return false; // 有显著提升，未收敛
        }
        return true;
    }

    @Override
    public List<FactorEvolutionRecord> getPromotedFactors() {
        return allRecords.stream()
                .filter(r -> r.getStatus() == FactorStatus.PROMOTED)
                .collect(Collectors.toList());
    }
}
