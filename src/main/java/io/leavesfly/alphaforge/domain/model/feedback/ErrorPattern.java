package io.leavesfly.alphaforge.domain.model.feedback;

/**
 * 信号错误模式 — 特定技术条件组合下的信号准确率统计
 *
 * 用于经验学习和 Few-shot 推理模板构建。
 */
public record ErrorPattern(
        String conditionSignature,
        double accuracy,
        int sampleSize
) {}
