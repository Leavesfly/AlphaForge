package io.leavesfly.alphaforge.domain.model.entity.user;

import java.time.LocalDateTime;

/**
 * 用户风险画像 — 驱动决策建议仓位"因人而异"。
 *
 * <p>单行记录表（id 恒为 1，UPSERT 语义）。未设置时应用层回退默认档位并标注 defaulted。</p>
 */
public class UserRiskProfile {

    public static final String CONSERVATIVE = "CONSERVATIVE";
    public static final String BALANCED = "BALANCED";
    public static final String AGGRESSIVE = "AGGRESSIVE";

    /** 主键（恒为 1） */
    private Long id;

    /** 风险承受档位: CONSERVATIVE(保守) / BALANCED(平衡) / AGGRESSIVE(激进) */
    private String riskTolerance;

    /** 总资金规模（万元），可空 */
    private Double capitalAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static boolean isValidTolerance(String tolerance) {
        return CONSERVATIVE.equals(tolerance) || BALANCED.equals(tolerance) || AGGRESSIVE.equals(tolerance);
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRiskTolerance() { return riskTolerance; }
    public void setRiskTolerance(String riskTolerance) { this.riskTolerance = riskTolerance; }
    public Double getCapitalAmount() { return capitalAmount; }
    public void setCapitalAmount(Double capitalAmount) { this.capitalAmount = capitalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
