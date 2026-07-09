package io.leavesfly.alphaforge.application.autonomy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L4 自主动作审计日志（内存环形缓冲 + SLF4J）。
 */
@Component
public class AutonomyAuditLog {

    private static final Logger log = LoggerFactory.getLogger(AutonomyAuditLog.class);
    private static final int MAX_ENTRIES = 500;

    private final List<Map<String, Object>> entries = Collections.synchronizedList(new ArrayList<>());

    public void record(String action, String entityType, String entityId,
                       String fromState, String toState, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", LocalDateTime.now().toString());
        entry.put("action", action);
        entry.put("entity_type", entityType);
        entry.put("entity_id", entityId);
        entry.put("from_state", fromState);
        entry.put("to_state", toState);
        entry.put("reason", reason);

        synchronized (entries) {
            entries.add(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
        }
        log.info("[AutonomyAudit] action={} entity={}:{} {}→{} reason={}",
                action, entityType, entityId, fromState, toState, reason);
    }

    public List<Map<String, Object>> recent(int limit) {
        synchronized (entries) {
            int from = Math.max(0, entries.size() - Math.max(1, limit));
            return new ArrayList<>(entries.subList(from, entries.size()));
        }
    }
}
