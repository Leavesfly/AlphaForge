package io.leavesfly.stock.infrastructure.storage;

import io.leavesfly.stock.AppInitializer;
import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.service.port.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储管理服务（基础设施实现）
 *
 * 功能:
 * 1. 报告文件存储(Markdown/JSON)
 * 2. 缓存管理(数据源缓存/LLM缓存)
 * 3. 数据目录管理
 * 4. 文件清理
 *
 * 实现 {@link StoragePort}，供 application 层通过端口依赖。
 */
@Service
public class StorageService implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final String baseDir;
    private final String reportDir;
    private final String cacheDir;
    private final String dataDir;

    /** 内存缓存 */
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    public StorageService(AppConfig config) {
        // 优先使用 STORAGE_DIR 环境变量覆盖；未设置时回退到 app.home/data (即 ~/.j_daily-stock-analysis/data)
        String appHome = System.getProperty(AppInitializer.PROP_APP_HOME, "");
        String defaultStorageDir = appHome.isEmpty() ? "./data" : appHome + "/data";
        this.baseDir = config.getEnv("STORAGE_DIR", defaultStorageDir);
        this.reportDir = baseDir + "/reports";
        this.cacheDir = baseDir + "/cache";
        this.dataDir = baseDir + "/stocks";
        initDirs();
    }

    private void initDirs() {
        try {
            Files.createDirectories(Path.of(reportDir));
            Files.createDirectories(Path.of(cacheDir));
            Files.createDirectories(Path.of(dataDir));
        } catch (IOException e) {
            log.error("初始化存储目录失败: {}", e.getMessage());
        }
    }

    // ========== 报告存储 ==========

    /**
     * 保存分析报告到文件
     */
    public String saveReport(String stockCode, String content, String format) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = String.format("%s_%s.%s", stockCode, date, format.equals("json") ? "json" : "md");
        String path = reportDir + "/" + date + "/" + filename;
        try {
            Files.createDirectories(Path.of(reportDir + "/" + date));
            Files.writeString(Path.of(path), content);
            log.debug("报告已保存: {}", path);
            return path;
        } catch (IOException e) {
            log.error("保存报告失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取报告文件
     */
    public String readReport(String stockCode, String date) {
        String mdPath = reportDir + "/" + date + "/" + stockCode + "_" + date + ".md";
        try {
            return Files.readString(Path.of(mdPath));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 获取报告列表
     */
    public List<Map<String, String>> listReports(int limit) {
        List<Map<String, String>> reports = new ArrayList<>();
        try {
            Path reportsPath = Path.of(reportDir);
            if (!Files.exists(reportsPath)) return reports;
            Files.list(reportsPath)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .limit(limit)
                    .forEach(dateDir -> {
                        try {
                            Files.list(dateDir)
                                    .filter(f -> f.toString().endsWith(".md"))
                                    .forEach(f -> reports.add(Map.of(
                                            "file", f.getFileName().toString(),
                                            "date", dateDir.getFileName().toString(),
                                            "path", f.toString()
                                    )));
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            log.error("列出报告失败: {}", e.getMessage());
        }
        return reports;
    }

    // ========== 缓存管理 ==========

    /**
     * 设置缓存(带TTL)
     */
    public void setCache(String key, String value, int ttlSeconds) {
        memoryCache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    /**
     * 获取缓存
     */
    public String getCache(String key) {
        CacheEntry entry = memoryCache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expireAt) {
            memoryCache.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 清除过期缓存
     */
    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        memoryCache.entrySet().removeIf(e -> now > e.getValue().expireAt);
    }

    /**
     * 持久化缓存到文件
     */
    public void saveCacheToFile(String key, String value) {
        String path = cacheDir + "/" + key.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cache";
        try {
            Files.writeString(Path.of(path), value);
        } catch (IOException e) {
            log.debug("缓存持久化失败: {}", key);
        }
    }

    /**
     * 从文件读取缓存
     */
    public String readCacheFromFile(String key) {
        String path = cacheDir + "/" + key.replaceAll("[^a-zA-Z0-9_-]", "_") + ".cache";
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return null;
        }
    }

    // ========== 数据管理 ==========

    /**
     * 保存股票数据到CSV
     */
    public void saveStockData(String stockCode, String csvContent) {
        String path = dataDir + "/" + stockCode + ".csv";
        try {
            Files.writeString(Path.of(path), csvContent);
        } catch (IOException e) {
            log.error("保存股票数据失败: {}", e.getMessage());
        }
    }

    /**
     * 获取存储统计
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("base_dir", baseDir);
        stats.put("memory_cache_size", memoryCache.size());
        try {
            long reportCount = Files.walk(Path.of(reportDir)).filter(p -> p.toString().endsWith(".md")).count();
            stats.put("report_count", reportCount);
            long cacheCount = Files.walk(Path.of(cacheDir)).filter(Files::isRegularFile).count();
            stats.put("cache_file_count", cacheCount);
        } catch (IOException e) {
            stats.put("report_count", "N/A");
        }
        return stats;
    }

    /**
     * 清理旧数据(保留最近N天)
     */
    public int cleanOldData(int keepDays) {
        int cleaned = 0;
        LocalDate cutoff = LocalDate.now().minusDays(keepDays);
        try {
            Path reportsPath = Path.of(reportDir);
            if (Files.exists(reportsPath)) {
                List<Path> oldDirs = Files.list(reportsPath)
                        .filter(Files::isDirectory)
                        .filter(p -> {
                            try {
                                return LocalDate.parse(p.getFileName().toString()).isBefore(cutoff);
                            } catch (Exception e) { return false; }
                        })
                        .collect(java.util.stream.Collectors.toList());
                for (Path dir : oldDirs) {
                    Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
                    cleaned++;
                }
            }
        } catch (IOException e) {
            log.error("清理旧数据失败: {}", e.getMessage());
        }
        return cleaned;
    }

    /** 缓存条目 */
    private static class CacheEntry {
        final String value;
        final long expireAt;
        CacheEntry(String value, long expireAt) { this.value = value; this.expireAt = expireAt; }
    }
}
