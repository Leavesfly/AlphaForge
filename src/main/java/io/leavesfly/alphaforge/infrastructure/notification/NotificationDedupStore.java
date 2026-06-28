package io.leavesfly.alphaforge.infrastructure.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通知去重持久化 — 写入 SQLite，重启后仍有效。
 */
@Component
public class NotificationDedupStore {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDedupStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initTable();
    }

    private void initTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS notification_dedup (
                    content_hash VARCHAR(64) PRIMARY KEY,
                    sent_at TIMESTAMP NOT NULL
                )
                """);
    }

    public boolean recentlySent(String hash, int dedupMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(dedupMinutes);
        Integer count = jdbcTemplate.query(
                "SELECT COUNT(*) FROM notification_dedup WHERE content_hash = ? AND sent_at > ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                hash, cutoff.toString());
        return count != null && count > 0;
    }

    public void recordSent(String hash) {
        jdbcTemplate.update(
                "INSERT OR REPLACE INTO notification_dedup (content_hash, sent_at) VALUES (?, ?)",
                hash, LocalDateTime.now().toString());
        jdbcTemplate.update(
                "DELETE FROM notification_dedup WHERE sent_at < ?",
                LocalDateTime.now().minusHours(24).toString());
    }
}
