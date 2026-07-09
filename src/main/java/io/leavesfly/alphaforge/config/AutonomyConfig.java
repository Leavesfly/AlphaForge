package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * L4 纸面闭环自主配置 — 通过环境变量加载，默认关闭自动执行/晋升。
 */
@Component
public class AutonomyConfig {

    private static final Logger log = LoggerFactory.getLogger(AutonomyConfig.class);

    private final EnvVarProvider env;

    private boolean enabled;
    private boolean autoExecuteSignals;
    private boolean autoPromote;
    private boolean autoDemote;
    private boolean autoApplyParams;
    private String minPromoteGrade;
    private double maxPositionPct;
    private double defaultPositionPct;
    private double maxDrawdownHaltPct;
    private double dailyLossHaltPct;
    private Long paperAccountId;
    private String factorEvolutionCron;

    public AutonomyConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        enabled = env.getBool("AUTONOMY_ENABLED", false);
        autoExecuteSignals = env.getBool("AUTONOMY_AUTO_EXECUTE_SIGNALS", false);
        autoPromote = env.getBool("AUTONOMY_AUTO_PROMOTE", false);
        autoDemote = env.getBool("AUTONOMY_AUTO_DEMOTE", true);
        autoApplyParams = env.getBool("AUTONOMY_AUTO_APPLY_PARAMS", false);
        minPromoteGrade = env.get("AUTONOMY_MIN_PROMOTE_GRADE", "B").trim().toUpperCase();
        maxPositionPct = env.getDouble("AUTONOMY_MAX_POSITION_PCT", 0.20);
        defaultPositionPct = env.getDouble("AUTONOMY_DEFAULT_POSITION_PCT", 0.10);
        maxDrawdownHaltPct = env.getDouble("AUTONOMY_MAX_DRAWDOWN_HALT_PCT", 15.0);
        dailyLossHaltPct = env.getDouble("AUTONOMY_DAILY_LOSS_HALT_PCT", 5.0);
        String accountStr = env.get("AUTONOMY_PAPER_ACCOUNT_ID", "");
        paperAccountId = accountStr.isBlank() ? null : Long.parseLong(accountStr.trim());
        factorEvolutionCron = env.get("AUTONOMY_FACTOR_EVOLUTION_CRON", "0 0 20 * * MON");

        log.info("AutonomyConfig: enabled={}, autoExecute={}, autoPromote={}, autoDemote={}, autoApplyParams={}, minGrade={}",
                enabled, autoExecuteSignals, autoPromote, autoDemote, autoApplyParams, minPromoteGrade);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isAutoExecuteSignals() { return enabled && autoExecuteSignals; }
    public boolean isAutoPromote() { return enabled && autoPromote; }
    public boolean isAutoDemote() { return enabled && autoDemote; }
    public boolean isAutoApplyParams() { return enabled && autoApplyParams; }
    public String getMinPromoteGrade() { return minPromoteGrade; }
    public double getMaxPositionPct() { return maxPositionPct; }
    public double getDefaultPositionPct() { return defaultPositionPct; }
    public double getMaxDrawdownHaltPct() { return maxDrawdownHaltPct; }
    public double getDailyLossHaltPct() { return dailyLossHaltPct; }
    public Long getPaperAccountId() { return paperAccountId; }
    public String getFactorEvolutionCron() { return factorEvolutionCron; }

    /** 运行时覆盖（测试 / AutonomyController） */
    public void setEnabledRuntime(boolean enabled) { this.enabled = enabled; }
    public void setAutoExecuteSignalsRuntime(boolean v) { this.autoExecuteSignals = v; }
    public void setAutoPromoteRuntime(boolean v) { this.autoPromote = v; }
    public void setAutoDemoteRuntime(boolean v) { this.autoDemote = v; }
    public void setAutoApplyParamsRuntime(boolean v) { this.autoApplyParams = v; }
    public void setPaperAccountIdRuntime(Long id) { this.paperAccountId = id; }
}
