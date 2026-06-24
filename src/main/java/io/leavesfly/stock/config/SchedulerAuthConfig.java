package io.leavesfly.stock.config;

import java.util.UUID;

/**
 * 调度 & 认证相关配置
 */
public class SchedulerAuthConfig {

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

    // Getters & Setters
    public String getAuthSecret() { return authSecret; }
    public void setAuthSecret(String authSecret) { this.authSecret = authSecret; }
    public String getAuthPassword() { return authPassword; }
    public void setAuthPassword(String authPassword) { this.authPassword = authPassword; }
    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean authEnabled) { this.authEnabled = authEnabled; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getAgentMode() { return agentMode; }
    public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
    public int getAgentMaxIterations() { return agentMaxIterations; }
    public void setAgentMaxIterations(int agentMaxIterations) { this.agentMaxIterations = agentMaxIterations; }
}
