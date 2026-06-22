package com.stock.agent;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent记忆系统
 * 
 * 对应Python版本的 src/agent/memory.py
 * 存储Agent的对话历史、分析经验和学到的模式
 */
@Component
public class AgentMemory {

    /** 短期记忆: sessionId -> 消息历史 */
    private final Map<String, List<MemoryEntry>> shortTermMemory = new ConcurrentHashMap<>();

    /** 长期记忆: 跨会话的重要结论 */
    private final List<MemoryEntry> longTermMemory = Collections.synchronizedList(new ArrayList<>());

    /** 最大短期记忆条数(每个session) */
    private static final int MAX_SHORT_TERM = 50;
    /** 最大长期记忆条数 */
    private static final int MAX_LONG_TERM = 200;

    /**
     * 添加短期记忆
     */
    public void addShortTerm(String sessionId, String role, String content) {
        List<MemoryEntry> memories = shortTermMemory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        memories.add(new MemoryEntry(role, content, LocalDateTime.now()));
        // 超出限制时移除最早的
        while (memories.size() > MAX_SHORT_TERM) {
            memories.remove(0);
        }
    }

    /**
     * 获取短期记忆(对话历史)
     */
    public List<MemoryEntry> getShortTerm(String sessionId) {
        return shortTermMemory.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * 转换短期记忆为LLM消息格式
     */
    public List<Map<String, String>> toMessages(String sessionId) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (MemoryEntry entry : getShortTerm(sessionId)) {
            messages.add(Map.of("role", entry.role, "content", entry.content));
        }
        return messages;
    }

    /**
     * 添加长期记忆(重要结论)
     */
    public void addLongTerm(String content, String category) {
        longTermMemory.add(new MemoryEntry("system", content, LocalDateTime.now(), category));
        while (longTermMemory.size() > MAX_LONG_TERM) {
            longTermMemory.remove(0);
        }
    }

    /**
     * 搜索长期记忆(关键词匹配)
     */
    public List<MemoryEntry> searchLongTerm(String keyword) {
        String lower = keyword.toLowerCase();
        List<MemoryEntry> results = new ArrayList<>();
        for (MemoryEntry entry : longTermMemory) {
            if (entry.content.toLowerCase().contains(lower)) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * 获取与特定股票相关的记忆
     */
    public List<MemoryEntry> getStockMemories(String stockCode) {
        return searchLongTerm(stockCode);
    }

    /**
     * 清除会话记忆
     */
    public void clearSession(String sessionId) {
        shortTermMemory.remove(sessionId);
    }

    /**
     * 获取记忆统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("active_sessions", shortTermMemory.size());
        stats.put("long_term_count", longTermMemory.size());
        stats.put("total_short_term", shortTermMemory.values().stream().mapToInt(List::size).sum());
        return stats;
    }

    /**
     * 记忆条目
     */
    public static class MemoryEntry {
        public final String role;
        public final String content;
        public final LocalDateTime timestamp;
        public final String category;

        public MemoryEntry(String role, String content, LocalDateTime timestamp) {
            this(role, content, timestamp, null);
        }

        public MemoryEntry(String role, String content, LocalDateTime timestamp, String category) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
            this.category = category;
        }
    }
}
