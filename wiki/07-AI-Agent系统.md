# AI Agent 系统

## 概述

系统实现了完整的多智能体（Multi-Agent）协作分析架构，通过 `AgentOrchestrator` 编排多个专业 Agent 进行分工协作，最终通过共识机制得出综合分析结论。

## 核心组件

```
AgentOrchestrator (编排器)
    │
    ├── AgentRuntime (运行时)
    │     ├── AgentMemory (记忆系统)
    │     └── AgentProtocols (协议定义)
    │
    ├── AgentExecutor (执行器)
    │     ├── LlmToolAdapter (LLM工具适配)
    │     └── skills/ (技能库)
    │
    └── Agents (专业Agent)
          ├── TechnicalAgent   (技术分析)
          ├── IntelAgent       (情报分析)
          ├── DecisionAgent    (决策建议)
          ├── RiskAgent        (风险评估)
          └── PortfolioAgent   (组合管理)
```

## Agent 类型

### TechnicalAgent（技术分析 Agent）

**职责**: 基于 K 线形态、技术指标（均线/MACD/RSI/布林带等）进行量化分析。

**输出**:
- 技术面评分
- 趋势判断（多头/空头/震荡）
- 关键支撑位和压力位
- 量价关系描述

### IntelAgent（情报分析 Agent）

**职责**: 整合新闻、公告、行业动态等信息进行基本面分析。

**输出**:
- 消息面评估
- 利好/利空判断
- 关键事件影响分析
- 行业趋势判断

### DecisionAgent（决策 Agent）

**职责**: 综合各 Agent 意见和市场环境，给出最终操作建议。

**输出**:
- 操作信号（buy/sell/hold）
- 综合评分
- 具体操作建议
- 仓位建议

### RiskAgent（风险评估 Agent）

**职责**: 评估投资风险等级和潜在风险因素。

**输出**:
- 风险等级评估
- 主要风险因素
- 止损建议价位
- 风险收益比

### PortfolioAgent（投资组合 Agent）

**职责**: 从投资组合角度评估个股配置价值。

**输出**:
- 配置建议权重
- 与组合相关性
- 分散化效果评估

## 编排模式

`AgentOrchestrator` 支持 4 种编排模式:

| 模式 | 参与 Agent | 适用场景 |
|------|-----------|---------|
| `quick` | TechnicalAgent | 快速技术面扫描 |
| `standard` | Technical + Intel + Decision | 日常分析（默认） |
| `full` | 所有 Agent | 深度全面分析 |
| `specialist` | 智能选择 | 根据上下文自动决定 |

### specialist 模式选择逻辑

```
始终执行: TechnicalAgent + RiskAgent
有新闻时追加: IntelAgent
最终执行: DecisionAgent
```

## 共识机制

当多个 Agent 返回各自的分析意见后，编排器通过以下算法达成共识：

### 1. 评分共识

```
最终评分 = 所有Agent评分的算术平均
```

### 2. 信号投票

```
最终信号 = 所有Agent信号中投票数最多的信号（多数决）
```

### 3. 置信度计算

```
置信度 = Agent置信度的平均值
高(>=0.7) / 中等(0.4~0.7) / 低(<0.4)
```

## AgentOpinion 数据结构

每个 Agent 返回的意见结构:

| 字段 | 类型 | 说明 |
|------|------|------|
| agentName | String | Agent 名称 |
| reasoning | String | 分析推理过程 |
| signal | String | 操作信号 (buy/sell/neutral/strong_buy/strong_sell) |
| score | int | 评分 (0-100) |
| confidence | double | 置信度 (0.0-1.0) |
| durationMs | long | 分析耗时(毫秒) |

## 记忆系统 (AgentMemory)

Agent 记忆系统用于存储和检索历史分析经验:

- **短期记忆**: 当前分析轮次的中间结果
- **长期记忆**: 历史分析模式和经验
- **上下文记忆**: 大盘环境、板块轮动等全局信息

## 与 Pipeline 的集成

Pipeline 中 Agent 模式的触发条件:

```java
if ("true".equalsIgnoreCase(config.getAgentMode()) || "full".equals(config.getAgentMode())) {
    // Agent 模式分析
    analysisResult = analyzeWithAgent(stockCode, stockName, enhancedContext, diag);
} else {
    // 传统 LLM 直接分析
    analysisResult = analyzeWithLlm(stockCode, stockName, enhancedContext);
}
```

当 Agent 模式分析失败时，自动降级到传统 LLM 模式：

```java
try {
    output = agentOrchestrator.runAnalysis(context);
} catch (Exception e) {
    // 降级到传统 LLM
    return analyzeWithLlm(stockCode, stockName, context);
}
```

## 配置

```properties
# 启用 Agent 模式
AGENT_MODE=true

# 完整 Agent 模式（所有 Agent 参与）
AGENT_MODE=full

# 禁用 Agent 模式（使用传统 LLM）
AGENT_MODE=false
```
