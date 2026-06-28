package io.leavesfly.stock.application.strategy.model;

import java.util.Collections;
import java.util.List;

/**
 * 策略完整定义，与 definitions/{id}.yaml 一一对应。
 *
 * 一条策略可同时具备多种能力（如 bull_trend 同时有 backtest 和 scoring 段），
 * 具体支持哪些能力由 catalog.yaml 中的 capabilities 字段声明。
 */
public class StrategyDefinition {

    private int schemaVersion;
    /** 策略唯一标识，如 ma_golden_cross */
    private String id;
    /** 中文展示名 */
    private String label;
    private String description;
    /** 策略分类，如 trend_following */
    private String category;
    /** 风险等级：low / medium / high */
    private String riskLevel;
    private BacktestProfile backtest;
    private ScreeningProfile screening;
    private ScoringProfile scoring;
    /** 来自 catalog 的能力列表：backtest、screening、scoring */
    private List<String> capabilities = Collections.emptyList();
    /** 实现状态：implemented / partial / planned */
    private String runtime;
    /** 运行时是否可用（校验通过且非 planned） */
    private boolean available = true;
    /** 不可用原因 */
    private String unavailableReason;

    // ========== 策略元数据（供 LLM 语义匹配与策略编排使用） ==========

    /** 适用市场阶段：bull / bear / range / recovery / all */
    private List<String> applicableMarket = Collections.emptyList();
    /** 适用市值类型：large / mid / small / all */
    private List<String> applicableCap = Collections.emptyList();
    /** 语义标签，供 LLM 按关键词匹配策略，如 "趋势" "突破" "主力" */
    private List<String> tags = Collections.emptyList();

    /** 是否配置了有效的综合评分段 */
    public boolean hasScoring() {
        return scoring != null && scoring.getScoreWeight() > 0;
    }

    /** 是否声明了指定能力 */
    public boolean supports(String capability) {
        return capabilities.contains(capability);
    }

    /** 是否配置了可执行的回测入场条件 */
    public boolean hasBacktest() {
        return backtest != null && !backtest.getEntryConditions().isEmpty();
    }

    /** 是否配置了选股打分规则 */
    public boolean hasScreening() {
        return screening != null && !screening.getScoringRules().isEmpty();
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public BacktestProfile getBacktest() { return backtest; }
    public void setBacktest(BacktestProfile backtest) { this.backtest = backtest; }

    public ScreeningProfile getScreening() { return screening; }
    public void setScreening(ScreeningProfile screening) { this.screening = screening; }

    public ScoringProfile getScoring() { return scoring; }
    public void setScoring(ScoringProfile scoring) { this.scoring = scoring; }

    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities != null ? capabilities : Collections.emptyList();
    }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getUnavailableReason() { return unavailableReason; }
    public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }

    public List<String> getApplicableMarket() { return applicableMarket; }
    public void setApplicableMarket(List<String> applicableMarket) {
        this.applicableMarket = applicableMarket != null ? applicableMarket : Collections.emptyList();
    }

    public List<String> getApplicableCap() { return applicableCap; }
    public void setApplicableCap(List<String> applicableCap) {
        this.applicableCap = applicableCap != null ? applicableCap : Collections.emptyList();
    }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : Collections.emptyList();
    }

    /** 判断策略是否适用于指定市场阶段 */
    public boolean isApplicableToMarket(String marketPhase) {
        if (applicableMarket.isEmpty() || applicableMarket.contains("all")) {
            return true;
        }
        return applicableMarket.contains(marketPhase);
    }

    /** 判断策略是否适用于指定市值类型 */
    public boolean isApplicableToCap(String capType) {
        if (applicableCap.isEmpty() || applicableCap.contains("all")) {
            return true;
        }
        return applicableCap.contains(capType);
    }
}
