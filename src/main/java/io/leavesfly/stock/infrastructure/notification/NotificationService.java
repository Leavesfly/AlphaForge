package io.leavesfly.stock.infrastructure.notification;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.stock.domain.model.enums.NotificationChannel;
import io.leavesfly.stock.domain.service.port.NotificationPort;

import io.leavesfly.stock.infrastructure.notification.sender.BaseNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 通知服务 - 多渠道通知推送
 *
 * 支持企业微信、飞书、钉钉、邮件通知渠道，自动路由和故障容错
 */
@Service
public class NotificationService implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AppConfig config;
    private final NotificationRouter notificationRouter;
    private final Map<NotificationChannel, BaseNotificationSender> senders = new LinkedHashMap<>();

    public NotificationService(AppConfig config, NotificationRouter notificationRouter,
                               List<BaseNotificationSender> senderList) {
        this.config = config;
        this.notificationRouter = notificationRouter;
        // 注册所有sender
        for (BaseNotificationSender sender : senderList) {
            senders.put(sender.getChannel(), sender);
        }
        log.info("通知服务初始化完成, 已注册 {} 个渠道", senders.size());
    }

    /**
     * 推送分析报告列表
     *
     * @param reports 分析报告列表
     */
    public void sendAnalysisReports(List<AnalysisReport> reports) {
        if (reports == null || reports.isEmpty()) return;
        
        List<NotificationChannel> channels = getEnabledChannels();
        if (channels.isEmpty()) {
            log.info("未配置通知渠道，跳过推送");
            return;
        }

        // 格式化报告内容
        String markdownContent = formatReportsToMarkdown(reports);
        String briefContent = formatReportsToBrief(reports);

        // 逐渠道推送
        for (NotificationChannel channel : channels) {
            BaseNotificationSender sender = senders.get(channel);
            if (sender == null) {
                log.warn("通知渠道 {} 未找到对应发送器", channel.getCode());
                continue;
            }
            
            try {
                // 根据渠道能力选择内容格式
                String content = sender.supportsMarkdown() ? markdownContent : briefContent;
                String title = buildTitle(reports);
                boolean success = sender.send(title, content);
                if (success) {
                    log.info("通知推送成功: {}", channel.getName());
                } else {
                    log.warn("通知推送失败: {}", channel.getName());
                }
            } catch (Exception e) {
                log.error("通知推送异常: {} - {}", channel.getName(), e.getMessage());
            }
        }
    }

    /**
     * 发送单条通知消息
     */
    public void sendMessage(String title, String content) {
        List<NotificationChannel> channels = getEnabledChannels();
        for (NotificationChannel channel : channels) {
            BaseNotificationSender sender = senders.get(channel);
            if (sender != null) {
                try {
                    sender.send(title, content);
                } catch (Exception e) {
                    log.error("消息推送失败: {} - {}", channel.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * 降噪判定：委托 NotificationRouter 判断相同内容在窗口期内是否应发送
     */
    @Override
    public boolean shouldSend(String title, String content) {
        return notificationRouter.shouldSend(title, content);
    }

    /**
     * 获取已启用的通知渠道列表
     */
    private List<NotificationChannel> getEnabledChannels() {
        String channelsStr = config.getNotificationChannels();
        if (channelsStr == null || channelsStr.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<NotificationChannel> enabled = new ArrayList<>();
        for (String code : channelsStr.split(",")) {
            NotificationChannel channel = NotificationChannel.fromCode(code.trim());
            if (channel != null) {
                enabled.add(channel);
            }
        }
        return enabled;
    }

    /**
     * 构建通知标题
     */
    private String buildTitle(List<AnalysisReport> reports) {
        if (reports.size() == 1) {
            AnalysisReport r = reports.get(0);
            return String.format("📊 %s(%s) 分析报告", r.getStockName(), r.getStockCode());
        }
        return String.format("📊 股票分析报告 (%d只)", reports.size());
    }

    /**
     * 格式化为Markdown报告
     */
    private String formatReportsToMarkdown(List<AnalysisReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📊 股票分析报告\n\n");
        sb.append("_分析时间: ").append(reports.get(0).getAnalysisDate()).append("_\n\n");

        for (AnalysisReport report : reports) {
            sb.append("## ").append(report.getStockName())
              .append("(").append(report.getStockCode()).append(")\n\n");
            
            if (report.getCurrentPrice() != null) {
                sb.append("- **当前价格**: ").append(String.format("%.2f", report.getCurrentPrice()));
                if (report.getChangePct() != null) {
                    sb.append(String.format(" (%+.2f%%)", report.getChangePct()));
                }
                sb.append("\n");
            }
            if (report.getSignal() != null) {
                sb.append("- **交易信号**: ").append(report.getSignal()).append("\n");
            }
            if (report.getTotalScore() != null) {
                sb.append("- **综合评分**: ").append(report.getTotalScore()).append("/100\n");
            }
            sb.append("\n");
            
            if (report.getFullReport() != null && !report.getFullReport().isEmpty()) {
                sb.append(report.getFullReport()).append("\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    /**
     * 格式化为简短文本(用于不支持Markdown的渠道)
     */
    private String formatReportsToBrief(List<AnalysisReport> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("【股票分析报告】\n\n");
        
        for (AnalysisReport report : reports) {
            sb.append(report.getStockName()).append("(").append(report.getStockCode()).append(")\n");
            if (report.getCurrentPrice() != null) {
                sb.append("价格: ").append(String.format("%.2f", report.getCurrentPrice()));
                if (report.getChangePct() != null) {
                    sb.append(String.format(" (%+.2f%%)", report.getChangePct()));
                }
                sb.append("\n");
            }
            if (report.getSignal() != null) {
                sb.append("信号: ").append(report.getSignal()).append("\n");
            }
            if (report.getSummary() != null) {
                sb.append("摘要: ").append(report.getSummary()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
