package io.leavesfly.alphaforge.infrastructure.memory;

import io.leavesfly.alphaforge.domain.service.port.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储 — 当未配置外部向量数据库时的默认实现
 *
 * 使用余弦相似度进行检索，适合小规模数据。
 * 生产环境应替换为 Milvus/Qdrant/pgvector 等。
 */
@Component
@ConditionalOnMissingBean(VectorStorePort.class)
public class InMemoryVectorStore implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    /** collection -> (id -> entry) */
    private final Map<String, Map<String, Entry>> collections = new ConcurrentHashMap<>();

    public InMemoryVectorStore() {
        log.info("使用内存向量存储（生产环境建议替换为 Milvus/Qdrant/pgvector）");
    }

    @Override
    public void upsert(String collection, String id, float[] vector, Map<String, Object> metadata) {
        collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                .put(id, new Entry(vector, metadata));
    }

    @Override
    public void upsertBatch(String collection, List<VectorEntry> entries) {
        for (VectorEntry entry : entries) {
            upsert(collection, entry.id, entry.vector, entry.metadata);
        }
    }

    @Override
    public List<SearchResult> search(String collection, float[] queryVector, int topK, Map<String, Object> filter) {
        Map<String, Entry> store = collections.get(collection);
        if (store == null || store.isEmpty() || queryVector.length == 0) {
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Entry> e : store.entrySet()) {
            Entry entry = e.getValue();
            // 元数据过滤
            if (filter != null && !matchesFilter(entry.metadata, filter)) continue;

            float score = cosineSimilarity(queryVector, entry.vector);
            results.add(new SearchResult(e.getKey(), score, entry.metadata));
        }

        results.sort((a, b) -> Float.compare(b.score, a.score));
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    @Override
    public void delete(String collection, String id) {
        Map<String, Entry> store = collections.get(collection);
        if (store != null) store.remove(id);
    }

    @Override
    public boolean collectionExists(String collection) {
        return collections.containsKey(collection);
    }

    @Override
    public void createCollection(String collection, int dimension) {
        collections.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
    }

    // ===== 辅助方法 =====

    private boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filter) {
        for (Map.Entry<String, Object> f : filter.entrySet()) {
            Object val = metadata.get(f.getKey());
            if (val == null || !val.toString().equals(f.getValue().toString())) {
                return false;
            }
        }
        return true;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 0 ? (float) (dot / denom) : 0;
    }

    private record Entry(float[] vector, Map<String, Object> metadata) {}
}
