package io.leavesfly.alphaforge.infrastructure.memory;

import io.leavesfly.alphaforge.domain.service.port.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认空实现 — 当未配置 Embedding 服务时使用
 *
 * 返回零向量，不执行实际嵌入。
 * 系统通过 AnalysisMemoryService.isEnabled() 检测并降级。
 */
@Component
@ConditionalOnMissingBean(EmbeddingPort.class)
public class NoOpEmbeddingService implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmbeddingService.class);

    @Override
    public float[] embed(String text) {
        return new float[0];
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(new float[0]);
        }
        return result;
    }

    @Override
    public int getDimension() {
        return 0;
    }

    @Override
    public String getModelName() {
        return "noop";
    }
}
