package io.leavesfly.alphaforge.application.service.feedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.leavesfly.alphaforge.domain.model.feedback.ErrorPattern;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 跨轮次经验记忆 — 系统自我进化的经验积累层
 *
 * 核心能力：
 * 1. 记录每次分析的条件和结果（技术指标快照 + 信号 + 效果）
 * 2. 识别"在什么条件下信号准/不准"的模式
 * 3. 在下次分析时注入经验提示（"上次在类似条件下，信号结果是 X"）
 *
 * 与 AnalysisMemoryService 的区别：
 * - AnalysisMemoryService 基于向量检索做语义相似匹配（需要 Embedding）
 * - ExperienceMemory 基于条件匹配做经验回溯（纯规则，无需外部依赖）
 */
@Service
public class ExperienceMemory {

    private static final Logger log = LoggerFactory.getLogger(ExperienceMemory.class);

    /** 每只股票最多保留的经验条数 */
    private static final int MAX_EXPERIENCES_PER_STOCK = 20;
    /** 全局经验上限 */
    private static final int MAX_GLOBAL_EXPERIENCES = 500;

    /** 股票代码 -> 经验列表 */
    private final Map<String, CopyOnWriteArrayList<Experience>> stockExperiences = new ConcurrentHashMap<>();
    /** 全局经验（跨股票的模式） */
    private final CopyOnWriteArrayList<Experience> globalExperiences = new CopyOnWriteArrayList<>();

    /**
     * 记录一次分析经验
     *
     * @param stockCode        股票代码
     * @param signal           交易信号
     * @param score            评分
     * @param confidence       置信度
     * @param technicalSnapshot 技术指标快照（关键字段：trend, rsi_status, ma_arrangement, macd_cross）
     * @param marketSentiment   大盘情绪
     */
    public void recordExperience(String stockCode, String signal, int score, String confidence,
                                  Map<String, Object> technicalSnapshot, String marketSentiment) {
        if (stockCode == null || signal == null) return;

        Experience exp = new Experience(
                stockCode, signal, score, confidence,
                extractConditionSignature(technicalSnapshot),
                marketSentiment,
                null, // outcome 尚未评估，后续更新
                null, // returnPct 尚未评估
                LocalDate.now().toString()
        );

        // 记录到股票维度
        CopyOnWriteArrayList<Experience> stockList = stockExperiences.computeIfAbsent(
                stockCode, k -> new CopyOnWriteArrayList<>());
        stockList.add(exp);
        while (stockList.size() > MAX_EXPERIENCES_PER_STOCK) {
            stockList.remove(0);
        }

        // 记录到全局维度
        globalExperiences.add(exp);
        while (globalExperiences.size() > MAX_GLOBAL_EXPERIENCES) {
            globalExperiences.remove(0);
        }

        log.debug("记录经验: {} signal={} score={}", stockCode, signal, score);
    }

    /**
     * 更新经验效果（当信号被评估后调用）
     *
     * @param stockCode 股票代码
     * @param date      分析日期
     * @param outcome   评估结果 (correct/incorrect/partial)
     * @param returnPct 实际收益率
     */
    public void updateOutcome(String stockCode, String date, String outcome, Double returnPct) {
        CopyOnWriteArrayList<Experience> list = stockExperiences.get(stockCode);
        if (list == null) return;

        for (int i = list.size() - 1; i >= 0; i--) {
            Experience exp = list.get(i);
            if (exp.date.equals(date) && exp.outcome == null) {
                list.set(i, exp.withOutcome(outcome, returnPct));
                break;
            }
        }
    }

    /**
     * 获取经验提示（注入到 LLM 分析 prompt 中）
     *
     * @param stockCode           股票代码
     * @param currentConditions   当前分析条件（技术指标快照）
     * @return 经验提示文本
     */
    public String getExperienceHint(String stockCode, Map<String, Object> currentConditions) {
        CopyOnWriteArrayList<Experience> list = stockExperiences.get(stockCode);
        if (list == null || list.isEmpty()) return "";

        String currentSignature = extractConditionSignature(currentConditions);

        // 查找条件相似的历史经验
        List<Experience> similar = new ArrayList<>();
        for (Experience exp : list) {
            if (exp.outcome == null) continue; // 跳过未评估的
            int similarity = calculateSimilarity(exp.conditionSignature, currentSignature);
            if (similarity > 0) {
                similar.add(exp);
            }
        }

        if (similar.isEmpty()) return "";

        // 按相似度排序，取前 3 条
        similar.sort((a, b) -> Integer.compare(
                calculateSimilarity(b.conditionSignature, currentSignature),
                calculateSimilarity(a.conditionSignature, currentSignature)));
        List<Experience> top = similar.subList(0, Math.min(3, similar.size()));

        return formatExperienceHint(top);
    }

    /**
     * 获取错误模式统计
     *
     * @return 错误模式列表（如"在多头排列+RSI超买时，买入信号准确率仅40%"）
     */
    public List<ErrorPattern> getErrorPatterns() {
        Map<String, int[]> patternStats = new LinkedHashMap<>(); // signature -> [correct, total]

        for (Experience exp : globalExperiences) {
            if (exp.outcome == null) continue;
            int[] counts = patternStats.computeIfAbsent(exp.conditionSignature, k -> new int[2]);
            counts[1]++;
            if ("correct".equals(exp.outcome) || "partial".equals(exp.outcome)) {
                counts[0]++;
            }
        }

        List<ErrorPattern> patterns = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : patternStats.entrySet()) {
            int[] counts = entry.getValue();
            if (counts[1] < 3) continue; // 样本不足，跳过
            double accuracy = (double) counts[0] / counts[1] * 100;
            if (accuracy < 50) {
                patterns.add(new ErrorPattern(entry.getKey(), accuracy, counts[1]));
            }
        }

        patterns.sort(Comparator.comparingDouble(ErrorPattern::accuracy));
        return patterns.stream().limit(5).toList();
    }

    // ===== 内部方法 =====

    /** 提取条件签名（用于相似度匹配） */
    private String extractConditionSignature(Map<String, Object> tech) {
        if (tech == null || tech.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // 趋势
        Object trend = tech.get("trend");
        if (trend != null) sb.append("T:").append(trend).append("|");
        // 均线排列
        Object ma = tech.get("ma_analysis");
        if (ma instanceof Map<?, ?> map) {
            sb.append("MA:").append(map.get("arrangement")).append("|");
        }
        // MACD
        Object macd = tech.get("macd");
        if (macd instanceof Map<?, ?> map) {
            sb.append("MACD:").append(map.get("cross")).append("|");
        }
        // RSI
        Object rsi = tech.get("rsi");
        if (rsi instanceof Map<?, ?> map) {
            sb.append("RSI:").append(map.get("status")).append("|");
        }
        return sb.toString();
    }

    /** 计算条件签名相似度（简单字符串匹配） */
    private int calculateSimilarity(String sig1, String sig2) {
        if (sig1 == null || sig2 == null || sig1.isEmpty() || sig2.isEmpty()) return 0;
        int matchCount = 0;
        String[] parts1 = sig1.split("\\|");
        String[] parts2 = sig2.split("\\|");
        for (String p1 : parts1) {
            for (String p2 : parts2) {
                if (p1.equals(p2)) matchCount++;
            }
        }
        return matchCount;
    }

    /** 格式化经验提示文本 */
    private String formatExperienceHint(List<Experience> experiences) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 相似条件下的历史经验\n");

        for (Experience exp : experiences) {
            sb.append(String.format("- [%s] 条件:%s 信号:%s → 结果:%s",
                    exp.date, exp.conditionSignature, exp.signal, exp.outcome));
            if (exp.returnPct != null) {
                sb.append(String.format(" 收益:%.1f%%", exp.returnPct));
            }
            sb.append("\n");
        }

        // 如果有错误经验，给出提示
        long errorCount = experiences.stream().filter(e -> "incorrect".equals(e.outcome)).count();
        if (errorCount > experiences.size() / 2) {
            sb.append("\n⚠ 当前条件与历史错误信号高度相似，请谨慎评估。\n");
        }

        return sb.toString();
    }

    // ===== 数据类 =====

    /** 经验记录 */
    private record Experience(
            String stockCode,
            String signal,
            int score,
            String confidence,
            String conditionSignature, // 技术指标条件签名
            String marketSentiment,
            String outcome,            // 评估结果（null=未评估）
            Double returnPct,           // 实际收益率（null=未评估）
            String date
    ) {
        Experience withOutcome(String outcome, Double returnPct) {
            return new Experience(stockCode, signal, score, confidence,
                    conditionSignature, marketSentiment, outcome, returnPct, date);
        }
    }

    /** 错误模式 — 已提取至 domain.model.feedback.ErrorPattern */
}
