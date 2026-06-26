package io.leavesfly.stock.infrastructure.notification;

import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知路由和降噪管理器
 */
@Component
public class NotificationRouter {

    private static final Logger log = LoggerFactory.getLogger(NotificationRouter.class);

    /** 降噪记录: 内容hash -> 上次发送时间（内存缓存） */
    private final Map<String, LocalDateTime> sentHistory = new ConcurrentHashMap<>();

    private final NotificationDedupStore dedupStore;

    /** 默认降噪间隔(分钟) */
    private static final int DEDUP_MINUTES = 30;

    /** 每小时最大通知数 */
    private static final int MAX_PER_HOUR = 20;

    /** 本小时已发送计数 */
    private int hourlyCount = 0;
    private int currentHour = -1;

    public NotificationRouter(NotificationDedupStore dedupStore) {
        this.dedupStore = dedupStore;
    }

    /**
     * 判断消息是否应该发送(降噪过滤)
     *
     * @param title   通知标题
     * @param content 通知内容
     * @return true表示可以发送
     */
    public boolean shouldSend(String title, String content) {
        // 频率限制
        int hour = LocalDateTime.now().getHour();
        if (hour != currentHour) {
            currentHour = hour;
            hourlyCount = 0;
        }
        if (hourlyCount >= MAX_PER_HOUR) {
            log.warn("通知频率超限，本小时已发送 {} 条，丢弃", hourlyCount);
            return false;
        }

        // 去重检查（DB 持久化 + 内存缓存）
        String hash = computeHash(title + content);
        if (dedupStore.recentlySent(hash, DEDUP_MINUTES)) {
            log.debug("通知去重(DB): 相同内容在 {} 分钟内已发送", DEDUP_MINUTES);
            return false;
        }
        LocalDateTime lastSent = sentHistory.get(hash);
        if (lastSent != null && lastSent.plusMinutes(DEDUP_MINUTES).isAfter(LocalDateTime.now())) {
            log.debug("通知去重: 相同内容在 {} 分钟内已发送", DEDUP_MINUTES);
            return false;
        }

        sentHistory.put(hash, LocalDateTime.now());
        dedupStore.recordSent(hash);
        hourlyCount++;

        // 清理过期记录(保留最近100条)
        if (sentHistory.size() > 100) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
            sentHistory.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        }

        return true;
    }

    /**
     * 路由消息到合适的渠道
     * 根据消息类型和渠道能力选择最佳渠道
     *
     * @param messageType 消息类型(report/alert/brief/image)
     * @param channels    可用渠道列表
     * @return 过滤后的渠道列表
     */
    public List<NotificationChannel> route(String messageType, List<NotificationChannel> channels) {
        if (channels == null || channels.isEmpty()) return Collections.emptyList();

        switch (messageType) {
            case "image":
                // 仅支持图片的渠道
                return channels.stream()
                        .filter(this::supportsImage)
                        .collect(java.util.stream.Collectors.toList());
            case "long_report":
                // 支持长文本的渠道
                return channels.stream()
                        .filter(ch -> getMaxLength(ch) > 4000)
                        .collect(java.util.stream.Collectors.toList());
            default:
                return new ArrayList<>(channels);
        }
    }

    /**
     * 获取渠道的内容格式能力
     */
    public Map<String, Object> getChannelCapabilities(NotificationChannel channel) {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("channel", channel.getCode());
        caps.put("supports_markdown", supportsMarkdown(channel));
        caps.put("supports_image", supportsImage(channel));
        caps.put("max_length", getMaxLength(channel));
        caps.put("supports_card", supportsCard(channel));
        return caps;
    }

    /**
     * 智能截断内容以适配渠道长度限制
     */
    public String adaptContent(String content, NotificationChannel channel) {
        int maxLen = getMaxLength(channel);
        if (content == null) return "";
        if (content.length() <= maxLen) return content;
        return content.substring(0, maxLen - 20) + "\n\n...(内容已截断，完整报告请查看系统)";
    }

    // ========== 渠道能力判断 ==========

    private boolean supportsMarkdown(NotificationChannel ch) {
        return ch == NotificationChannel.FEISHU || ch == NotificationChannel.WECOM ||
               ch == NotificationChannel.DINGTALK;
    }

    private boolean supportsImage(NotificationChannel ch) {
        return ch == NotificationChannel.EMAIL || ch == NotificationChannel.FEISHU;
    }

    private boolean supportsCard(NotificationChannel ch) {
        return ch == NotificationChannel.FEISHU || ch == NotificationChannel.WECOM ||
               ch == NotificationChannel.DINGTALK;
    }

    private int getMaxLength(NotificationChannel ch) {
        switch (ch) {
            case WECOM: return 4096;
            case FEISHU: return 30000;
            case DINGTALK: return 20000;
            case EMAIL: return 100000;
            default: return 4096;
        }
    }

    private String computeHash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(digest).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
