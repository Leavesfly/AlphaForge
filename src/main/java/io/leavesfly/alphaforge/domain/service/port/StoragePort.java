package io.leavesfly.alphaforge.domain.service.port;

import java.util.List;
import java.util.Map;

/**
 * 存储端口接口（依赖倒置）
 *
 * 报告存读、缓存管理、数据目录等文件系统存储契约。
 * 具体实现由 infrastructure.storage 提供，遵循整洁架构端口-适配器模式。
 */
public interface StoragePort {

    // ========== 报告存储 ==========

    /** 保存分析报告到文件，返回文件路径 */
    String saveReport(String stockCode, String content, String format);

    /** 读取报告文件 */
    String readReport(String stockCode, String date);

    /** 获取报告列表 */
    List<Map<String, String>> listReports(int limit);

    // ========== 缓存管理 ==========

    /** 设置缓存(带TTL) */
    void setCache(String key, String value, int ttlSeconds);

    /** 获取缓存 */
    String getCache(String key);

    /** 清除过期缓存 */
    void cleanExpiredCache();

    /** 持久化缓存到文件 */
    void saveCacheToFile(String key, String value);

    /** 从文件读取缓存 */
    String readCacheFromFile(String key);

    // ========== 数据管理 ==========

    /** 保存股票数据到CSV */
    void saveStockData(String stockCode, String csvContent);

    /** 获取存储统计 */
    Map<String, Object> getStorageStats();

    /** 清理旧数据(保留最近N天)，返回清理数量 */
    int cleanOldData(int keepDays);
}
