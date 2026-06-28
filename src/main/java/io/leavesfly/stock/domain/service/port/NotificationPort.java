package io.leavesfly.stock.domain.service.port;

import io.leavesfly.stock.domain.model.entity.analysis.AnalysisReport;

import java.util.List;

/**
 * 通知端口（依赖倒置）
 *
 * application 层通过此端口推送通知并查询降噪判定，
 * 具体实现由 infrastructure.notification 提供（内部组合渠道发送器与降噪路由器）。
 */
public interface NotificationPort {

    /** 推送分析报告列表 */
    void sendAnalysisReports(List<AnalysisReport> reports);

    /** 推送单条消息 */
    void sendMessage(String title, String content);

    /** 降噪判定：相同内容在窗口期内是否应被发送 */
    boolean shouldSend(String title, String content);
}
