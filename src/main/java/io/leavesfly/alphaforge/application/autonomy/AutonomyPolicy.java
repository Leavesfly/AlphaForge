package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import org.springframework.stereotype.Component;

/**
 * L4 自主策略门面 — 统一读取开关与晋升门槛。
 */
@Component
public class AutonomyPolicy {

    private final AutonomyConfig config;
    private final AutonomyAuditLog auditLog;

    public AutonomyPolicy(AutonomyConfig config, AutonomyAuditLog auditLog) {
        this.config = config;
        this.auditLog = auditLog;
    }

    public AutonomyConfig getConfig() { return config; }
    public AutonomyAuditLog getAuditLog() { return auditLog; }

    public boolean isEnabled() { return config.isEnabled(); }
    public boolean canAutoExecuteSignals() { return config.isAutoExecuteSignals(); }
    public boolean canAutoPromote() { return config.isAutoPromote(); }
    public boolean canAutoDemote() { return config.isAutoDemote(); }
    public boolean canAutoApplyParams() { return config.isAutoApplyParams(); }

    public Long paperAccountId() { return config.getPaperAccountId(); }

    public double positionPct() {
        return Math.min(config.getDefaultPositionPct(), config.getMaxPositionPct());
    }

    public double maxPositionPct() { return config.getMaxPositionPct(); }
    public double maxDrawdownHaltPct() { return config.getMaxDrawdownHaltPct(); }
    public double dailyLossHaltPct() { return config.getDailyLossHaltPct(); }

    public StrategyQualityScore.QualityGrade minPromoteGrade() {
        try {
            return StrategyQualityScore.QualityGrade.valueOf(config.getMinPromoteGrade());
        } catch (Exception e) {
            return StrategyQualityScore.QualityGrade.B;
        }
    }

    /** 等级是否达到晋升门槛（A > B > C > D） */
    public boolean meetsPromoteGrade(StrategyQualityScore.QualityGrade grade) {
        if (grade == null) return false;
        return gradeOrdinal(grade) <= gradeOrdinal(minPromoteGrade());
    }

    private static int gradeOrdinal(StrategyQualityScore.QualityGrade g) {
        return switch (g) {
            case A -> 0;
            case B -> 1;
            case C -> 2;
            case D -> 3;
        };
    }

    public void audit(String action, String entityType, String entityId,
                      String fromState, String toState, String reason) {
        auditLog.record(action, entityType, entityId, fromState, toState, reason);
    }
}
