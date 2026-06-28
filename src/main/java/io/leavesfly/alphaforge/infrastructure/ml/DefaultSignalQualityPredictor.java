package io.leavesfly.alphaforge.infrastructure.ml;

import io.leavesfly.alphaforge.domain.service.port.SignalQualityPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认信号质量预测器 — 基于规则的简单实现
 *
 * 当未配置 ML 模型时使用，基于历史信号准确率和共识度进行简单预测。
 * 生产环境应替换为 ML 模型实现（如 XGBoost/LightGBM）。
 */
@Component
@ConditionalOnMissingBean(SignalQualityPredictor.class)
public class DefaultSignalQualityPredictor implements SignalQualityPredictor {

    private static final Logger log = LoggerFactory.getLogger(DefaultSignalQualityPredictor.class);

    @Override
    public QualityPrediction predict(String signal, int score, String confidence,
                                      Map<String, Object> technicalContext,
                                      Map<String, Object> marketContext) {
        // 基于规则的简单质量预测
        double baseAccuracy = 0.55; // 基础准确率

        // 高置信度提升准确率
        if ("高".equals(confidence)) baseAccuracy += 0.15;
        else if ("低".equals(confidence)) baseAccuracy -= 0.15;

        // 极端评分（接近 0 或 100）通常可靠性较低
        if (score >= 90 || score <= 10) baseAccuracy -= 0.1;

        // 中性信号准确率通常更高
        if ("neutral".equals(signal)) baseAccuracy += 0.1;

        baseAccuracy = Math.max(0.1, Math.min(0.9, baseAccuracy));

        String adjustedConfidence = confidence;
        boolean suggestDowngrade = false;
        String reason = "规则式预测";

        if (baseAccuracy < 0.4) {
            adjustedConfidence = "低";
            suggestDowngrade = score >= 85 || score <= 15;
            reason = "信号可靠性预测较低(" + String.format("%.0f%%", baseAccuracy * 100) + ")";
        }

        return new QualityPrediction(baseAccuracy, adjustedConfidence, suggestDowngrade, reason);
    }

    @Override
    public boolean isReady() {
        return true; // 规则式预测始终可用
    }
}
