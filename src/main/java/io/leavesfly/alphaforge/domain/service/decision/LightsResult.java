package io.leavesfly.alphaforge.domain.service.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三灯评估结果 — 不可变值对象，由 {@link ThreeLightsEngine} 构建一次成型。
 *
 * <p>三灯规则为纪律预设值，未经样本外验证；toMap() 输出供 REST 端点、
 * Agent 转述与前端展示共用同一结构。</p>
 */
public class LightsResult {

    private final String stockCode;
    private final String stockName;
    private final Verdict verdict;
    private final Map<String, LightResult> lights;
    /** 趋势分（unrated 时为 null） */
    private final Double trendScore;
    private final Map<String, Object> snapshot;
    private final VerdictMatrix.Decision decision;
    /** ATR 交易计划（仅行动态生成，可空） */
    private final TradePlan plan;
    /** 左侧分批计划（左侧观察且价深绿时，可空） */
    private final Map<String, Object> leftPlan;
    /** 持仓联动明细（有持仓成本时，可空） */
    private final Map<String, Object> position;
    /** 市场环境上下文（应用层透传，可空） */
    private final Map<String, Object> marketContext;
    private final List<Evidence> evidence;
    /** 评估基准日（末根 K 线日期） */
    private final String asof;
    private final int nBars;

    private LightsResult(Builder b) {
        this.stockCode = b.stockCode;
        this.stockName = b.stockName;
        this.verdict = b.verdict;
        this.lights = Map.copyOf(b.lights);
        this.trendScore = b.trendScore;
        this.snapshot = Map.copyOf(b.snapshot);
        this.decision = b.decision;
        this.plan = b.plan;
        this.leftPlan = b.leftPlan;
        this.position = b.position;
        this.marketContext = b.marketContext;
        this.evidence = List.copyOf(b.evidence);
        this.asof = b.asof;
        this.nBars = b.nBars;
    }

    public static Builder builder(String stockCode) {
        return new Builder(stockCode);
    }

    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public Verdict getVerdict() { return verdict; }
    public Map<String, LightResult> getLights() { return lights; }
    public Double getTrendScore() { return trendScore; }
    public Map<String, Object> getSnapshot() { return snapshot; }
    public VerdictMatrix.Decision getDecision() { return decision; }
    public TradePlan getPlan() { return plan; }
    public Map<String, Object> getLeftPlan() { return leftPlan; }
    public Map<String, Object> getPosition() { return position; }
    public Map<String, Object> getMarketContext() { return marketContext; }
    public List<Evidence> getEvidence() { return evidence; }
    public String getAsof() { return asof; }
    public int getNBars() { return nBars; }

    /** 三灯速览："价绿+势绿+时黄"（Agent 转述与横幅展示用） */
    public String lightsSummary() {
        return "价" + lightCn("value") + "+势" + lightCn("trend") + "+时" + lightCn("timing");
    }

    private String lightCn(String name) {
        LightResult light = lights.get(name);
        return light != null ? light.getColor().getCn() : "灰";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stockCode", stockCode);
        map.put("stockName", stockName);
        map.put("verdict", verdict.name().toLowerCase());
        map.put("verdictCn", verdict.getCn());
        map.put("lightsSummary", lightsSummary());
        Map<String, Object> lightsMap = new LinkedHashMap<>();
        lights.forEach((name, light) -> lightsMap.put(name, light.toMap()));
        map.put("lights", lightsMap);
        map.put("trendScore", trendScore);
        map.put("snapshot", new LinkedHashMap<>(snapshot));
        map.put("decision", decision.toMap());
        map.put("plan", plan != null ? plan.toMap() : null);
        map.put("leftPlan", leftPlan != null ? new LinkedHashMap<>(leftPlan) : null);
        map.put("position", position != null ? new LinkedHashMap<>(position) : null);
        map.put("marketContext", marketContext != null ? new LinkedHashMap<>(marketContext) : null);
        List<Map<String, Object>> evidenceList = new ArrayList<>();
        for (Evidence e : evidence) {
            evidenceList.add(e.toMap());
        }
        map.put("evidence", evidenceList);
        map.put("asof", asof);
        map.put("nBars", nBars);
        return map;
    }

    /**
     * 结构化证据链条目 — Agent 转述时可引用编号（E01、E02…）。
     */
    public record Evidence(String id, String light, String indicator, Object value,
                           Object threshold, boolean triggered, String impact, String claim) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("light", light);
            map.put("indicator", indicator);
            map.put("value", value);
            map.put("threshold", threshold);
            map.put("triggered", triggered);
            map.put("impact", impact);
            map.put("claim", claim);
            return map;
        }
    }

    public static class Builder {
        private final String stockCode;
        private String stockName;
        private Verdict verdict;
        private Map<String, LightResult> lights = new LinkedHashMap<>();
        private Double trendScore;
        private Map<String, Object> snapshot = new LinkedHashMap<>();
        private VerdictMatrix.Decision decision;
        private TradePlan plan;
        private Map<String, Object> leftPlan;
        private Map<String, Object> position;
        private Map<String, Object> marketContext;
        private List<Evidence> evidence = new ArrayList<>();
        private String asof = "";
        private int nBars;

        private Builder(String stockCode) {
            this.stockCode = stockCode;
        }

        public Builder stockName(String stockName) { this.stockName = stockName; return this; }
        public Builder verdict(Verdict verdict) { this.verdict = verdict; return this; }
        public Builder lights(Map<String, LightResult> lights) { this.lights = lights; return this; }
        public Builder trendScore(Double trendScore) { this.trendScore = trendScore; return this; }
        public Builder snapshot(Map<String, Object> snapshot) { this.snapshot = snapshot; return this; }
        public Builder decision(VerdictMatrix.Decision decision) { this.decision = decision; return this; }
        public Builder plan(TradePlan plan) { this.plan = plan; return this; }
        public Builder leftPlan(Map<String, Object> leftPlan) { this.leftPlan = leftPlan; return this; }
        public Builder position(Map<String, Object> position) { this.position = position; return this; }
        public Builder marketContext(Map<String, Object> marketContext) { this.marketContext = marketContext; return this; }
        public Builder evidence(List<Evidence> evidence) { this.evidence = evidence; return this; }
        public Builder asof(String asof) { this.asof = asof; return this; }
        public Builder nBars(int nBars) { this.nBars = nBars; return this; }

        public LightsResult build() {
            return new LightsResult(this);
        }
    }
}
