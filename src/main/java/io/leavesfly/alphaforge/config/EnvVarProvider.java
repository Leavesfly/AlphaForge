package io.leavesfly.alphaforge.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 环境变量统一读取器 — 从 dotenv 文件 + 系统环境变量加载配置
 *
 * 提取自 AppConfig，使各子配置（LlmConfig、NotificationConfig 等）
 * 能独立加载环境变量，无需依赖 AppConfig 这个上帝配置类。
 */
@Component
public class EnvVarProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvVarProvider.class);

    private Dotenv dotenv;

    @PostConstruct
    public void init() {
        try {
            dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            log.warn("未找到.env文件，使用系统环境变量");
            dotenv = null;
        }
    }

    public String get(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) return value;
        if (dotenv != null) {
            value = dotenv.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, "");
        if (value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String value = get(key, "");
        if (value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = get(key, "");
        if (value.isEmpty()) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }
}
