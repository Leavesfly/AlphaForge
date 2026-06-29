package io.leavesfly.alphaforge.application.factor.evolution;

/**
 * 因子进化配置 — 控制进化过程的参数
 *
 * 对应论文 AlphaAgentEvo 中的 "Evolution Hyperparameters"。
 */
public class FactorEvolutionConfig {

    /** 最大进化代数（达到后自动停止） */
    private int maxGenerationRounds = 10;

    /** 每轮生成的因子候选数 */
    private int candidatesPerRound = 8;

    /** 每轮选出的 Top 因子数（用于变异/交叉） */
    private int topKSelection = 3;

    /** 变异概率（0-1，每轮 Top 因子中进行变异的比例） */
    private double mutationRate = 0.6;

    /** 交叉繁殖概率（0-1，每轮 Top 因子中进行交叉的比例） */
    private double crossbreedRate = 0.3;

    /** 反向变异概率（0-1，每轮失败因子中进行反向变异的比例） */
    private double inverseMutateRate = 0.1;

    // ===== 评估门槛 =====

    /** 最低 IC（信息系数）要求 */
    private double minIC = 0.03;

    /** 最低 IR（信息比率）要求 */
    private double minIR = 0.5;

    /** 最低夏普比率要求 */
    private double minSharpe = 0.8;

    /** 最低综合评分要求（0-100） */
    private double minOverallScore = 60.0;

    // ===== 评估参数 =====

    /** 回测周期（天数） */
    private int backtestPeriodDays = 252;

    /** IC 计算的前瞻天数 */
    private int forwardDays = 5;

    /** 收敛判定代数（连续 N 代无显著提升则收敛） */
    private int convergenceGenerations = 3;

    /** 收敛判定阈值（IC 提升幅度小于此值视为无显著提升） */
    private double convergenceThreshold = 0.005;

    /** 是否启用深度回测（委托 BacktestSimulator 执行完整交易模拟） */
    private boolean enableDeepBacktest = true;

    /** 深度回测的初始资金 */
    private double deepBacktestCapital = 100000;

    /** 因子表达式沙箱超时（毫秒） */
    private long sandboxTimeoutMs = 5000;

    // ===== 评分权重 =====

    /** IC 权重 */
    private double weightIC = 0.35;
    /** IR 权重 */
    private double weightIR = 0.25;
    /** 夏普比率权重 */
    private double weightSharpe = 0.20;
    /** 最大回撤惩罚权重 */
    private double weightDrawdown = 0.10;
    /** 换手率惩罚权重 */
    private double weightTurnover = 0.10;

    public static FactorEvolutionConfig defaultConfig() {
        return new FactorEvolutionConfig();
    }

    public static FactorEvolutionConfig aggressive() {
        FactorEvolutionConfig config = new FactorEvolutionConfig();
        config.maxGenerationRounds = 20;
        config.candidatesPerRound = 15;
        config.topKSelection = 5;
        config.minIC = 0.02;
        config.minIR = 0.3;
        config.minSharpe = 0.5;
        return config;
    }

    public static FactorEvolutionConfig conservative() {
        FactorEvolutionConfig config = new FactorEvolutionConfig();
        config.maxGenerationRounds = 5;
        config.candidatesPerRound = 5;
        config.topKSelection = 2;
        config.minIC = 0.05;
        config.minIR = 0.8;
        config.minSharpe = 1.0;
        config.minOverallScore = 70.0;
        return config;
    }

    // ===== Getters & Setters =====

    public int getMaxGenerationRounds() { return maxGenerationRounds; }
    public void setMaxGenerationRounds(int maxGenerationRounds) { this.maxGenerationRounds = maxGenerationRounds; }
    public int getCandidatesPerRound() { return candidatesPerRound; }
    public void setCandidatesPerRound(int candidatesPerRound) { this.candidatesPerRound = candidatesPerRound; }
    public int getTopKSelection() { return topKSelection; }
    public void setTopKSelection(int topKSelection) { this.topKSelection = topKSelection; }
    public double getMutationRate() { return mutationRate; }
    public void setMutationRate(double mutationRate) { this.mutationRate = mutationRate; }
    public double getCrossbreedRate() { return crossbreedRate; }
    public void setCrossbreedRate(double crossbreedRate) { this.crossbreedRate = crossbreedRate; }
    public double getInverseMutateRate() { return inverseMutateRate; }
    public void setInverseMutateRate(double inverseMutateRate) { this.inverseMutateRate = inverseMutateRate; }
    public double getMinIC() { return minIC; }
    public void setMinIC(double minIC) { this.minIC = minIC; }
    public double getMinIR() { return minIR; }
    public void setMinIR(double minIR) { this.minIR = minIR; }
    public double getMinSharpe() { return minSharpe; }
    public void setMinSharpe(double minSharpe) { this.minSharpe = minSharpe; }
    public double getMinOverallScore() { return minOverallScore; }
    public void setMinOverallScore(double minOverallScore) { this.minOverallScore = minOverallScore; }
    public int getBacktestPeriodDays() { return backtestPeriodDays; }
    public void setBacktestPeriodDays(int backtestPeriodDays) { this.backtestPeriodDays = backtestPeriodDays; }
    public int getForwardDays() { return forwardDays; }
    public void setForwardDays(int forwardDays) { this.forwardDays = forwardDays; }
    public int getConvergenceGenerations() { return convergenceGenerations; }
    public void setConvergenceGenerations(int convergenceGenerations) { this.convergenceGenerations = convergenceGenerations; }
    public double getConvergenceThreshold() { return convergenceThreshold; }
    public void setConvergenceThreshold(double convergenceThreshold) { this.convergenceThreshold = convergenceThreshold; }
    public boolean isEnableDeepBacktest() { return enableDeepBacktest; }
    public void setEnableDeepBacktest(boolean enableDeepBacktest) { this.enableDeepBacktest = enableDeepBacktest; }
    public double getDeepBacktestCapital() { return deepBacktestCapital; }
    public void setDeepBacktestCapital(double deepBacktestCapital) { this.deepBacktestCapital = deepBacktestCapital; }
    public long getSandboxTimeoutMs() { return sandboxTimeoutMs; }
    public void setSandboxTimeoutMs(long sandboxTimeoutMs) { this.sandboxTimeoutMs = sandboxTimeoutMs; }
    public double getWeightIC() { return weightIC; }
    public void setWeightIC(double weightIC) { this.weightIC = weightIC; }
    public double getWeightIR() { return weightIR; }
    public void setWeightIR(double weightIR) { this.weightIR = weightIR; }
    public double getWeightSharpe() { return weightSharpe; }
    public void setWeightSharpe(double weightSharpe) { this.weightSharpe = weightSharpe; }
    public double getWeightDrawdown() { return weightDrawdown; }
    public void setWeightDrawdown(double weightDrawdown) { this.weightDrawdown = weightDrawdown; }
    public double getWeightTurnover() { return weightTurnover; }
    public void setWeightTurnover(double weightTurnover) { this.weightTurnover = weightTurnover; }
}
