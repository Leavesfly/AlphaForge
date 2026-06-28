package io.leavesfly.stock.infrastructure.persistence.chat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话与消息持久化
 */
@Mapper
public interface ChatRepository {

    // ===== 会话操作 =====

    void insertSession(@Param("sessionId") String sessionId,
                       @Param("title") String title,
                       @Param("now") LocalDateTime now);

    void updateSessionActive(@Param("sessionId") String sessionId,
                             @Param("messageCount") int messageCount,
                             @Param("title") String title,
                             @Param("now") LocalDateTime now);

    void deleteSession(@Param("sessionId") String sessionId);

    List<Map<String, Object>> findSessions(@Param("limit") int limit);

    Map<String, Object> findSessionById(@Param("sessionId") String sessionId);

    // ===== 消息操作 =====

    void insertMessage(@Param("sessionId") String sessionId,
                       @Param("messageId") String messageId,
                       @Param("role") String role,
                       @Param("content") String content,
                       @Param("skillName") String skillName,
                       @Param("now") LocalDateTime now);

    List<Map<String, Object>> findMessagesBySessionId(@Param("sessionId") String sessionId);

    void deleteMessagesBySessionId(@Param("sessionId") String sessionId);

    int countMessagesBySessionId(@Param("sessionId") String sessionId);
}
