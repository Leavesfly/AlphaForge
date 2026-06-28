package io.leavesfly.alphaforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用初始化器 - 在Spring容器启动前执行
 *
 * 职责:
 * 1. 确定运行时数据根目录: ~/.j_daily-alphaforge-analysis/
 * 2. 自动创建必要的目录结构(data/, logs/)
 * 3. 设置系统属性 app.home 供 application.yml 和 logback.xml 引用
 * 4. 执行环境预检查(JDK版本、目录可写性等)
 */
public class AppInitializer {

    /** 运行时数据根目录名 */
    private static final String APP_DIR_NAME = ".j_daily-alphaforge-analysis";

    /** 系统属性 key，供配置文件通过 ${app.home} 引用 */
    public static final String PROP_APP_HOME = "app.home";

    /** 需要自动创建的子目录 */
    private static final String[] SUB_DIRS = {"data", "logs"};

    /**
     * 执行初始化，在 SpringApplication.run() 之前调用
     */
    public static void init() {
        Path appHome = resolveAppHome();

        // 设置系统属性，供 application.yml / logback.xml 使用
        System.setProperty(PROP_APP_HOME, appHome.toString());

        // 创建目录结构
        ensureDirectories(appHome);

        // 环境预检查
        List<String> errors = preflight(appHome);
        if (!errors.isEmpty()) {
            System.err.println("========================================");
            System.err.println("应用初始化检查失败:");
            for (String err : errors) {
                System.err.println("  ✗ " + err);
            }
            System.err.println("========================================");
            System.exit(1);
        }

        System.out.println("[Init] 运行时数据目录: " + appHome);
    }

    /**
     * 解析运行时数据根目录
     * 优先使用环境变量 APP_HOME，否则默认 ~/.j_daily-alphaforge-analysis
     */
    private static Path resolveAppHome() {
        String envHome = System.getenv("APP_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Paths.get(envHome).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home"), APP_DIR_NAME).toAbsolutePath();
    }

    /**
     * 确保目录结构存在
     */
    private static void ensureDirectories(Path appHome) {
        try {
            Files.createDirectories(appHome);
            for (String sub : SUB_DIRS) {
                Files.createDirectories(appHome.resolve(sub));
            }
        } catch (IOException e) {
            System.err.println("无法创建数据目录: " + appHome + " - " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 环境预检查
     */
    private static List<String> preflight(Path appHome) {
        List<String> errors = new ArrayList<>();

        // 检查 JDK 版本
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 17) {
            errors.add("需要 JDK 17+，当前版本: " + javaVersion);
        }

        // 检查数据目录可写
        Path dataDir = appHome.resolve("data");
        if (!Files.isWritable(dataDir)) {
            errors.add("数据目录不可写: " + dataDir);
        }

        // 检查日志目录可写
        Path logsDir = appHome.resolve("logs");
        if (!Files.isWritable(logsDir)) {
            errors.add("日志目录不可写: " + logsDir);
        }

        return errors;
    }
}
