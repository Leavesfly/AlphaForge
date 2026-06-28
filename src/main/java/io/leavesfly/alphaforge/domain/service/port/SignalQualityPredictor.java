package io.leavesfly.alphaforge.domain.service.port;

import java.util.Map;

/**
 * 信号质量预测端口（依赖倒置）
 *
 * 使用 ML 模型或统计方法预测 LLM 生成信号的质量/可靠性。
 * 具体实现可对接在线学习模型、XGBoost、或基于历史准确率的统计模型。
 *
 * 当未配置实现时，系统使用 SignalVerifier 的规则式验证。
 */
public interface SignalQualityPredictor {

    /**
     * 预测信号质量
     *
     * @param signal           交易信号 (strong_buy/buy/neutral/sell/strong_sell)
     * @param score            综合评分 (0-100)
     * @param confidence       置信度 (高/中等/低)
     * @param technicalContext 技术分析上下文
     * @param marketContext    市场环境上下文
     * @return 质量预测结果
     */
    QualityPrediction predict(String signal, int score, String confidence,
                               Map<String, Object> technicalContext,
                               Map<String, Object> marketContext);

    /** 模型是否已就绪 */
    boolean isReady();

    // ===== 数据类 =====

    /** 质量预测结果 */
    class QualityPrediction {
        /** 预测准确率 (0.0-1.0) */
        public final double predictedAccuracy;
        /** 建议调整的置信度 (高/中等/低) */
        public final String adjustedConfidence;
        /** 是否建议降级信号 */
        public final boolean suggestDowngrade;
        /** 预测理由 */
        public final String reason;

        public QualityPrediction(double predictedAccuracy, String adjustedConfidence,
                                  boolean suggestDowngrade, String reason) {
            this.predictedAccuracy = predictedAccuracy;
            this.adjustedConfidence = adjustedConfidence;
            this.suggestDowngrade = suggestDowngrade;
            this.reason = reason;
        }
    }
}
