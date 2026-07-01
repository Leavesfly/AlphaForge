# 因子自进化系统

## 概述

因子自进化系统是 AlphaForge 的核心创新模块，基于论文 **FactorMiner** 和 **AlphaAgentEvo** 的思路，通过 LLM 驱动的遗传算法自动发现、评估、进化量化因子，实现「AI 锻造 Alpha」的目标。

## 架构设计

```
FactorEvolutionOrchestrator（进化编排器）
    │
    ├── FactorGeneratorAgent（因子生成器）
    │      └── 调用 LLM 根据历史经验生成因子表达式
    │
    ├── FactorEvaluator（因子评估器）
    │      ├── FactorExpressionExecutor（表达式执行引擎）
    │      └── BacktestSimulator（回测验证）
    │
    ├── FactorEvolutionMemory（进化记忆）
    │      └── 持久化历史进化记录，跨代经验传承
    │
    └── EvolvableFactorLibrary（可进化因子库）
           └── 管理已验证的优质因子
```

## 进化闭环流程

```
第 0 代（初始种群）
  generatorAgent.generateInitialFactors(context)
        ↓
评估（IC/IR 计算 + 多空回测）
  evaluator.evaluateBatch(candidates, universe)
        ↓
选择（按综合评分排序，保留 Top K）
  selectTopFactors(evaluations, config)
        ↓
记录进化历史
  evolutionMemory.record(candidate, evaluation, status)
        ↓
提升优质因子到生产因子库
  evolvableLibrary.promoteValidated(topFactors)
        ↓
收敛检查
  ├── 已收敛 → 返回 EvolutionResult
  └── 未收敛 → 进行变异
        ├── mutateFactors()       参数变异
        ├── crossbreedFactors()   因子杂交
        └── inverseMutate()       逆向变异
        ↓
进入下一代
```

## 因子变异类型

| 变异类型（MutationType） | 说明 |
|---|---|
| `INITIAL` | 第 0 代随机生成 |
| `PARAM_MUTATE` | 参数变异（调整窗口期、阈值等数值参数） |
| `EXPR_MUTATE` | 表达式变异（修改算子或组合方式） |
| `CROSSBREED` | 因子杂交（合并两个父代因子的逻辑） |
| `INVERSE_MUTATE` | 逆向变异（对已知失败因子取反方向） |
| `CONDITION_SPECIALIZE` | 条件特化（在特定市场条件下专化因子） |
| `COMBINE` | 因子组合（线性/非线性组合已有因子） |

## 因子生命周期

```
CANDIDATE（候选）
    ↓ 提交评估
EVALUATING（评估中）
    ↓
    ├── 通过阈值 → VALIDATED（已验证）
    │         ↓
    │       PROMOTED（已提升到生产库）
    │
    └── 未通过  → DEPRECATED（已淘汰）
                        ↓
               提取失败模式（FailurePattern）
               → 下一代生成时注入为负向约束
```

## 因子评估指标

### IC（信息系数）

\[
IC = \text{Spearman}(f_t, r_{t+5})
\]

- `f_t`：第 t 日的因子值截面排序
- `r_{t+5}`：第 t+5 日的股票收益率截面排序
- IC 均值越高越好，|IC| > 0.03 一般认为有显著预测力

### IR（信息比率）

\[
IR = \frac{\overline{IC}}{\sigma_{IC}}
\]

- IC 序列的均值与标准差之比
- IR > 0.5 认为因子质量良好

### 多空组合绩效

通过 `BacktestSimulator` 验证因子的多空组合收益：

- **夏普比率**：年化超额收益 / 年化超额波动率
- **最大回撤**：多空组合的最大净值回撤
- **胜率**：因子信号方向正确的比例
- **覆盖率**：有效因子值覆盖的股票比例（防止因子过于稀疏）

### 综合评分

```
综合评分 = w1 × IC均值 + w2 × IR + w3 × 夏普比率
           + w4 × 覆盖率 + w5 × 换手率得分
```

通过阈值判断是否 `isPassing`，决定因子是否晋升。

## 因子表达式 DSL

因子通过字符串表达式描述，由 `FactorExpressionExecutor` 解释执行：

```
# 示例因子表达式（量价动量因子）
momentum_vol_20d = (close - delay(close, 20)) / delay(close, 20) * volume_sum(20)

# 组合因子示例
quality_growth = roe * (1 - debt_ratio) / pe
```

支持的算子类别：
- **价格算子**：`close`, `open`, `high`, `low`
- **成交量算子**：`volume`, `amount`, `turnover`
- **时序算子**：`delay(x, n)`, `ts_mean(x, n)`, `ts_std(x, n)`
- **截面算子**：`rank(x)`, `normalize(x)`, `winsorize(x)`
- **数学算子**：`+`, `-`, `*`, `/`, `log`, `abs`, `sign`

## 经验记忆双轨注入

系统通过两套经验记忆为下一代因子生成提供指导：

### FactorEvolutionMemory（因子级经验）

存储每一代因子的进化历史，生成下一代时提供：
- 历史高分因子的表达式（正向 few-shot）
- 常见失败模式（负向约束）
- 各变异类型的成功率统计

### ExperienceMemory（信号级经验）

来自 `SignalFeedbackLoop` 的信号评估结果，通过 `FactorExperienceBridge` 桥接：
- 将信号准确率反馈给因子评估模块
- 高胜率信号背后的因子获得额外奖励权重
- 低胜率信号背后的因子模式被标记为负向约束

## 评估股票池（Universe）

默认使用沪深300成分股的子集作为评估股票池：

```java
List<String> evaluationUniverse = List.of(
    "600519", "000858", "601318", "600036", "000333",
    "600276", "000651", "601166", "002415", "300750"
);
```

可通过 `FactorEvolutionConfig.universe` 自定义评估股票池。

## 数据模型

### FactorCandidate（因子候选）

| 字段 | 说明 |
|---|---|
| `factorId` | 因子 UUID |
| `factorName` | 因子名称（如 `vol_weighted_momentum_20d`） |
| `factorExpression` | 因子计算表达式（DSL） |
| `factorType` | 类型：SIMPLE/COMPOSITE/CROSS_SECTIONAL/TIME_SERIES/EVENT_DRIVEN |
| `category` | 分类：momentum/mean_reversion/volatility/volume/trend/custom |
| `generationRound` | 进化代数 |
| `parentFactorId` | 父代因子 ID |
| `mutationType` | 变异类型 |
| `status` | 生命周期状态 |

### FactorEvaluation（因子评估结果）

| 字段 | 说明 |
|---|---|
| `ic`, `icMean`, `icStd` | IC 统计量 |
| `ir` | 信息比率 |
| `sharpeRatio` | 夏普比率 |
| `maxDrawdownPct` | 最大回撤 |
| `winRatePct` | 胜率 |
| `totalReturnPct` | 多空组合总收益 |
| `coverageRate` | 覆盖率 |
| `turnoverRate` | 换手率 |
| `icDecayDays` | IC 衰减天数（超过此天数后预测力消失） |
| `overallScore` | 综合评分（0-100） |
| `isPassing` | 是否通过晋升阈值 |

## 数据库表

因子自进化系统使用 5 张专用表：

| 表名 | 说明 |
|---|---|
| `factor_candidates` | 因子候选（含遗传信息） |
| `factor_evaluations` | 评估指标结果 |
| `factor_evolution_memory` | 跨代进化记忆 |
| `factor_failure_patterns` | 失败模式统计（防止同类因子重复出错） |
| `evolved_factor_registry` | 已提升到生产库的优质因子 |

详细字段定义见 [data-model.md](data-model.md)。
