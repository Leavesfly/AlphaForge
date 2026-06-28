package io.leavesfly.alphaforge.domain.service.port;

import java.util.List;
import java.util.Map;

/**
 * 向量存储与检索端口（依赖倒置）
 *
 * 提供向量数据的存储、更新和相似度检索能力。
 * 具体实现可对接 Milvus、Qdrant、pgvector、Chroma 等。
 */
public interface VectorStorePort {

    /**
     * 插入或更新向量
     *
     * @param collection 集合名称（如 "analysis_reports", "kline_patterns"）
     * @param id         唯一标识
     * @param vector     向量数据
     * @param metadata   元数据（如 stockCode, date, signal 等可过滤字段）
     */
    void upsert(String collection, String id, float[] vector, Map<String, Object> metadata);

    /**
     * 批量插入或更新向量
     *
     * @param collection 集合名称
     * @param entries    条目列表
     */
    void upsertBatch(String collection, List<VectorEntry> entries);

    /**
     * 向量相似度检索
     *
     * @param collection   集合名称
     * @param queryVector  查询向量
     * @param topK         返回前 K 条最相似结果
     * @param filter       元数据过滤条件（可为 null）
     * @return 检索结果列表（按相似度降序）
     */
    List<SearchResult> search(String collection, float[] queryVector, int topK, Map<String, Object> filter);

    /**
     * 删除向量
     *
     * @param collection 集合名称
     * @param id         唯一标识
     */
    void delete(String collection, String id);

    /**
     * 检查集合是否存在
     */
    boolean collectionExists(String collection);

    /**
     * 创建集合（指定向量维度）
     */
    void createCollection(String collection, int dimension);

    // ===== 数据类 =====

    /** 向量条目 */
    class VectorEntry {
        public final String id;
        public final float[] vector;
        public final Map<String, Object> metadata;

        public VectorEntry(String id, float[] vector, Map<String, Object> metadata) {
            this.id = id;
            this.vector = vector;
            this.metadata = metadata;
        }
    }

    /** 检索结果 */
    class SearchResult {
        public final String id;
        public final float score;
        public final Map<String, Object> metadata;

        public SearchResult(String id, float score, Map<String, Object> metadata) {
            this.id = id;
            this.score = score;
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return String.format("SearchResult{id='%s', score=%.4f, metadata=%s}", id, score, metadata);
        }
    }
}
