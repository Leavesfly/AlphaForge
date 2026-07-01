package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 调度 & 认证 & Agent 配置 — 独立 Spring Bean
 */
@Component
public class SchedulerAuthConfig {

    private final EnvVarProvider env;

    public SchedulerAuthConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        authSecret = env.get("AUTH_SECRET", "");
        if (authSecret.isEmpty()) authSecret = UUID.randomUUID().toString();
        authPassword = env.get("AUTH_PASSWORD", "");
        authEnabled = env.getBool("AUTH_ENABLED", false);
        scheduleCron = env.get("SCHEDULE_CRON", "0 0 18 * * MON-FRI");
        timezone = env.get("TIMEZONE", "Asia/Shanghai");
        agentMode = env.get("AGENT_MODE", "standard");
        agentMaxIterations = env.getInt("AGENT_MAX_ITERATIONS", 10);
    }

    // ========== 认证配置 ==========
    private String authSecret = UUID.randomUUID().toString();
    private String authPassword = "";
    private boolean authEnabled = false;

    // ========== 调度配置 ==========
    private String scheduleCron = "0 0 18 * * MON-FRI";
    private String timezone = "Asia/Shanghai";

    // ========== Agent配置 ==========
    private String agentMode = "standard";
    private int agentMaxIterations = 10;

    // Runtime setters (for AuthController test/API)
    public void setAuthPasswordRuntime(String password) { this.authPassword = password; }
    public void setAuthEnabledRuntime(boolean enabled) { this.authEnabled = enabled; }
    public void setAuthSecretRuntime(String secret) { this.authSecret = secret; }

    // Getters
    public String getAuthSecret() { return authSecret; }
    public String getAuthPassword() { return authPassword; }
    public boolean isAuthEnabled() { return authEnabled; }
    public String getScheduleCron() { return scheduleCron; }
    public String getTimezone() { return timezone; }
    public String getAgentMode() { return agentMode; }
    public int getAgentMaxIterations() { return agentMaxIterations; }
}
