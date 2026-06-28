package io.leavesfly.alphaforge.domain.service.port;

import java.util.List;

/**
 * 向量嵌入端口（依赖倒置）
 *
 * 将文本、K 线形态、分析报告等转为高维向量，用于语义检索和相似度匹配。
 * 具体实现可对接 OpenAI Embedding、阿里通义 Embedding 或本地模型。
 */
public interface EmbeddingPort {

    /**
     * 将单条文本转为向量
     *
     * @param text 输入文本
     * @return 向量表示（维度由具体模型决定，如 1024/1536）
     */
    float[] embed(String text);

    /**
     * 批量嵌入（减少 API 调用次数）
     *
     * @param texts 文本列表
     * @return 向量列表（与输入顺序一致）
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     */
    int getDimension();

    /**
     * 获取模型名称（如 text-embedding-v3 / text-embedding-3-small）
     */
    String getModelName();
}
