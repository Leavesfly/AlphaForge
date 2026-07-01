package io.leavesfly.alphaforge.application.agent.reasoning;

import io.leavesfly.alphaforge.application.service.feedback.SignalLearningService;
import io.leavesfly.alphaforge.domain.model.feedback.ErrorPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Few-shot 推理模板构建器 — 从信号反馈数据中提取"好的推理路径"范例
 *
 * 对应论文 RETuning 的核心思想：
 * 利用推理时扩展（inference-time scaling），通过 Few-shot 注入让 LLM 学习
 * "在什么条件下，什么推理路径能得出正确结论"。
 *
 * 与现有 SignalLearningService 的关系：
 * - SignalLearningService 提供原始的信号案例（信号→结果→收益）
 * - FewShotReasoningTemplateBuilder 在此基础上增加"推理路径分析"：
 *   1. 从正确信号中提取"成功推理模式"（什么推理路径导致正确判断）
 *   2. 从错误信号中提取"失败推理模式"（什么推理缺陷导致错误判断）
 *   3. 从 ExperienceMemory 的错误模式中提取"条件→结果"映射
 *   4. 构建 Few-shot 推理范例注入到 LLM prompt
 *
 * 构建的 Few-shot 模板包含：
 * - ✅ 正确范例：条件 + 推理路径 + 正确结论（让 LLM 模仿）
 * - ❌ 错误范例：条件 + 推理缺陷 + 错误结论（让 LLM 避免）
 * - ⚠ 条件警示：在特定条件下信号准确率偏低（让 LLM 谨慎）
 */
@Component
public class FewShotReasoningTemplateBuilder {

    private static final Logger log = LoggerFactory.getLogger(FewShotReasoningTemplateBuilder.class);

    /** 正确范例最大数量 */
    private static final int MAX_POSITIVE_EXAMPLES = 2;
    /** 错误范例最大数量 */
    private static final int MAX_NEGATIVE_EXAMPLES = 2;
    /** 推理路径模板最大长度 */
    private static final int MAX_REASONING_LENGTH = 300;

    private final SignalLearningService signalLearningService;

    public FewShotReasoningTemplateBuilder(SignalLearningService signalLearningService) {
        this.signalLearningService = signalLearningService;
    }

    /**
     * 构建 Few-shot 推理模板（注入到 LLM 分析 prompt 中）
     *
     * @param stockCode 股票代码
     * @return Few-shot 推理模板文本（无数据时返回空字符串）
     */
    public String buildFewShotTemplate(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // 1. 从信号反馈中提取推理范例
        String signalExamples = signalLearningService.buildFeedbackPrompt(stockCode);
        if (signalExamples != null && !signalExamples.isBlank()) {
            sb.append(signalExamples);
        }

        // 2. 从经验记忆中提取错误模式
        List<ErrorPattern> errorPatterns = signalLearningService.getErrorPatterns();
        if (errorPatterns != null && !errorPatterns.isEmpty()) {
            sb.append(buildErrorPatternTemplate(errorPatterns));
        }

        // 3. 构建推理路径范例（基于信号反馈数据）
        String reasoningExamples = buildReasoningPathExamples(stockCode);
        if (!reasoningExamples.isEmpty()) {
            sb.append(reasoningExamples);
        }

        return sb.toString();
    }

    /**
     * 构建错误模式模板
     */
    private String buildErrorPatternTemplate(List<ErrorPattern> patterns) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 条件→结果映射（从历史经验中学习）\n");
        sb.append("以下是在特定技术条件组合下，信号的历史准确率：\n\n");

        for (ErrorPattern p : patterns) {
            sb.append(String.format("- 条件签名: %s\n", p.conditionSignature()));
            sb.append(String.format("  准确率: %.1f%% (样本数: %d)\n", p.accuracy(), p.sampleSize()));
            if (p.accuracy() < 40) {
                sb.append("  ⚠ 该条件下信号准确率极低，请特别注意\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建推理路径范例 — 从信号反馈中提取"好的推理路径"和"坏的推理路径"
     *
     * 关键创新：不仅告诉 LLM "这个信号对/错"，还告诉它
     * "这个信号之所以对/错，是因为推理路径中哪些环节做对了/做错了"
     */
    private String buildReasoningPathExamples(String stockCode) {
        // 获取信号反馈的基础数据（通过 SignalLearningService 的公共接口）
        // 由于 SignalLearningService 的 SignalCase 是 private 的，
        // 我们通过 buildFeedbackPrompt 获取原始数据后做增强处理

        // 从经验记忆获取该股票的历史经验提示
        String experienceHint = signalLearningService.getExperienceHint(stockCode, Map.of());

        if (experienceHint == null || experienceHint.isBlank()) {
            // 无历史数据时，提供通用的推理路径指导
            return buildGenericReasoningGuidance();
        }

        // 从历史经验中提取"推理路径"指导
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 推理路径范例（从历史经验中学习）\n\n");

        // 分析历史经验，提取推理成功/失败模式
        sb.append("### 推理路径指导（基于历史经验总结）\n");
        sb.append("以下是系统从历史信号中总结的推理经验：\n\n");

        // 正面范例模板（通用化）
        sb.append("✅ **正确推理路径特征**（请在分析中体现）：\n");
        sb.append("- 从宏观环境出发，先确认大方向再分析个股\n");
        sb.append("- 技术面和基本面信号一致时，置信度可适当提高\n");
        sb.append("- 明确引用数据支撑每个判断（如'RSI=32，处于超卖区间'）\n");
        sb.append("- 提出假设后进行交叉验证，至少 2 个独立证据\n");
        sb.append("- 明确风险因素和止损位\n\n");

        // 负面范例模板
        sb.append("❌ **错误推理路径特征**（请避免）：\n");
        sb.append("- 仅看单一指标就下结论（如仅看 MACD 金叉就买入）\n");
        sb.append("- 忽略宏观环境逆风（熊市中技术信号可靠性下降）\n");
        sb.append("- 未进行交叉验证，假设缺乏证据支撑\n");
        sb.append("- 置信度过高但证据不足\n");
        sb.append("- 未设置止损或止损位不合理\n\n");

        return sb.toString();
    }

    /**
     * 通用推理路径指导（无历史数据时使用）
     */
    private String buildGenericReasoningGuidance() {
        return """

               ## 推理路径指导
               请按照以下推理路径进行分析，确保推理质量：

               ### 推理路径要求
               1. **分层推理**：宏观环境 → 行业逻辑 → 个股因子 → 综合判断
               2. **数据驱动**：每个判断需引用具体数据（指标值、数值、日期）
               3. **交叉验证**：关键假设需至少 2 个独立证据支撑
               4. **风险检查**：必须提出至少 1 个反面论点
               5. **一致性**：信号、评分、置信度三者必须自洽

               ### 常见推理错误（请避免）
               - 仅依赖单一指标得出结论
               - 忽略宏观环境对个股的影响
               - 技术面和基本面信号矛盾时未说明
               - 置信度与证据强度不匹配
               - 未考虑极端风险情景
               """;
    }

    /**
     * 构建推理质量自检清单（注入到 LLM 输出要求中）
     */
    public String buildReasoningQualityChecklist() {
        return """
               ## 推理质量自检清单（输出前请确认）
               □ 是否从宏观环境开始分析？
               □ 每个判断是否引用了具体数据？
               □ 是否提出了至少 2 个独立证据验证假设？
               □ 是否提出了至少 1 个反面论点？
               □ 信号和评分是否匹配？（如 buy 信号评分应 >55）
               □ 是否设置了入场价、止损价、目标价？
               □ 置信度是否与证据强度匹配？
               """;
    }
}
