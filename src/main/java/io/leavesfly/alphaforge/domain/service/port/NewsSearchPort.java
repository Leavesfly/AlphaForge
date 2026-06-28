package io.leavesfly.alphaforge.domain.service.port;

import java.util.List;
import java.util.Map;

/**
 * 新闻搜索端口接口（依赖倒置）
 *
 * 业务层通过此接口获取新闻数据，具体实现由基础设施层的 Adapter 提供。
 * 遵循整洁架构的端口-适配器模式（Ports & Adapters）。
 */
public interface NewsSearchPort {

    /**
     * 获取搜索提供商名称
     */
    String getProviderName();

    /**
     * 搜索新闻
     *
     * @param query      搜索关键词
     * @param maxResults 最大结果数
     * @return 新闻列表，每条新闻包含 title, url, content, source, published_date
     */
    List<Map<String, Object>> search(String query, int maxResults);

    /**
     * 判断当前适配器是否可用（API Key 已配置等）
     */
    boolean isAvailable();
}
