package io.leavesfly.alphaforge.infrastructure.llm;

import io.leavesfly.alphaforge.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM 渠道管理器 — 多渠道管理与故障切换
 *
 * 职责：
 * - 持有渠道列表与当前活跃渠道索引
 * - 提供渠道遍历接口（按故障切换顺序）
 * - 记录成功渠道，下次优先使用
 *
 * 线程安全说明：currentChannelIndex 使用 volatile 保证可见性，
 * 多线程并发切换渠道时最坏情况是同时使用不同渠道，不影响正确性。
 */
public class LlmChannelManager {

    private static final Logger log = LoggerFactory.getLogger(LlmChannelManager.class);

    private final List<AppConfig.LlmChannelConfig> channels;
    private volatile int currentChannelIndex = 0;

    public LlmChannelManager(List<AppConfig.LlmChannelConfig> channels) {
        this.channels = channels != null ? channels : List.of();
        log.info("LlmChannelManager 初始化: {} 个渠道 - {}",
                this.channels.size(),
                this.channels.stream().map(AppConfig.LlmChannelConfig::getModel).toList());
    }

    /** 渠道列表是否为空 */
    public boolean isEmpty() {
        return channels.isEmpty();
    }

    /** 获取渠道列表 */
    public List<AppConfig.LlmChannelConfig> getChannels() {
        return channels;
    }

    /** 获取渠道数量 */
    public int size() {
        return channels.size();
    }

    /**
     * 获取故障切换序列中指定位置的渠道
     *
     * @param attempt 第几次尝试（0=当前渠道，1=下一个，...）
     * @return 渠道配置
     */
    public AppConfig.LlmChannelConfig getChannel(int attempt) {
        int idx = (currentChannelIndex + attempt) % channels.size();
        return channels.get(idx);
    }

    /**
     * 获取故障切换序列中指定位置的渠道索引
     *
     * @param attempt 第几次尝试
     * @return 渠道索引
     */
    public int getChannelIndex(int attempt) {
        return (currentChannelIndex + attempt) % channels.size();
    }

    /**
     * 标记指定渠道为成功（下次优先使用）
     *
     * @param idx 成功的渠道索引
     */
    public void markSuccess(int idx) {
        if (idx >= 0 && idx < channels.size()) {
            this.currentChannelIndex = idx;
        }
    }

    /** 获取当前活跃渠道索引 */
    public int getCurrentChannelIndex() {
        return currentChannelIndex;
    }

    /** 获取当前活跃渠道 */
    public AppConfig.LlmChannelConfig getCurrentChannel() {
        if (channels.isEmpty()) return null;
        return channels.get(currentChannelIndex % channels.size());
    }
}
