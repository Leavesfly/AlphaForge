# Agent 系统详解

## 概述

AlphaForge 的 Agent 系统基于多 Agent 协作架构，支持四种分析模式，并内置完整的降级链，确保即便高级功能不可用时系统也能正常工作。

## Agent 模式与降级链

```
配置 agent_mode = debate / multi / react / single

运行时降级链：
  debate  →  multi  →  react  →  llm（直接调用）
    ↓失败       ↓失败      ↓失败
  自动降级   自动降级   自动降级
```

| 模式 | 触发条件 | 特点 |
|---|---|---|
| `debate` | `agentMode = debate` | 三轮辩论，置信度最高，耗时最长 |
| `multi` | `agentMode = multi/full` | 多 Agent 并行，均衡深度与速度 |
| `react` | 默认 | 单 Agent 工具调用，适合对话场景 |
| `llm` | 最终降级 | 直接 LLM 调用，速度最快 |

## 专业化 Agent

### 子 Agent 体系

```
SubAgent（接口）
    └── AbstractSpecializedAgent（基类）
            ├── TechnicalAgent     # 技术面分析
            ├── FundamentalAgent   # 基本面分析
            └── RiskAgent          # 风险审核
```

每个 SubAgent 实现 `analyze(stockCode, stockName, context)` 方法，返回 `AgentResult`。

**AgentResult 结构：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `agentName` | String | Agent 名称 |
| `role` | String | Agent 角色（technical/fundamental/risk） |
| `signal` | String | 信号（buy/sell/hold） |
| `score` | Integer | 评分（0-100） |
| `confidence` | String | 置信度 |
| `analysis` | String | 分析文本 |
| `keyFindings` | List\<String\> | 关键发现要点 |
| `durationMs` | long | 耗时（毫秒） |

### Prompt 模板

各专业 Agent 的系统 Prompt 存放于 `resources/prompts/`：

| 文件 | 对应 Agent |
|---|---|
| `technical_agent_system.yaml` | TechnicalAgent |
| `fundamental_agent_system.yaml` | FundamentalAgent |
| `risk_agent_system.yaml` | RiskAgent |
| `synthesis_system.yaml` + `synthesis_user.yaml` | MultiAgentOrchestrator 综合 |
| `intelligence_system.yaml` | 情报 Agent |
| `stock_analysis_system.yaml` | 通用分析 |

## MultiAgentOrchestrator（多 Agent 编排器）

### 工作流程

```
orchestrate(stockCode, stockName, context, timeout)
  │
  ├─ 并行提交各 SubAgent 任务（ExecutorService，线程数 = min(agentCount, 3)）
  │      ├── TechnicalAgent.analyze()
  │      ├── FundamentalAgent.analyze()
  │      └── RiskAgent.analyze()
  │
  ├─ 等待所有 Agent 完成（超时则返回空结果）
  │
  └─ synthesizeResults()
       └─ 调用 LLM 综合各维度结论
            ├─ 优先使用 synthesis_system.yaml + synthesis_user.yaml
            └─ 可选：StructuredReasoningPromptBuilder 注入结构化推理链
```

### OrchestrationResult 结构

| 字段 | 说明 |
|---|---|
| `agentResults` | 各 Agent 的分析结果列表 |
| `synthesis` | LLM 综合的最终分析文本 |
| `durationMs` | 总耗时 |

## AgentDebateOrchestrator（辩论编排器）

### 三轮辩论流程

灵感来源于论文 **ContestTrade** 和 **FactorMAD**。

```
Round 1 — 独立分析
  └── 复用 MultiAgentOrchestrator.orchestrate()
      └── 各 Agent 独立给出初步结论（互不知晓他人观点）

Round 2 — 交叉质询
  └── 将所有 Agent 的初步结论作为 Prompt 上下文
  └── 让 LLM 模拟每个 Agent 的视角进行交叉质询
  └── 每个 Agent 需声明立场：SUPPORT / OPPOSE / NEUTRAL
  └── 风控 Agent 的 OPPOSE 具有"一票否决权"效力

Round 3 — 裁判裁决
  └── LLM 综合 Round1 结论 + Round2 论点
  └── 输出最终信号（可能与原始信号不同）
  └── 记录共识度（DebateVerdict.consensusLevel）
```

### 关键数据模型

| 类名 | 说明 |
|---|---|
| `DebateRound` | 单轮辩论记录（轮次编号、类型、论点列表） |
| `DebateArgument` | 单个论点（Agent名、立场、论点内容、证据） |
| `DebateVerdict` | 裁判裁决（最终信号、最终评分、共识度） |
| `DebateResult` | 辩论总结果（含三轮所有数据） |

**DebateArgument.Stance 枚举：**
- `SUPPORT`：支持当前综合结论
- `OPPOSE`：反对，指出其他 Agent 的分析漏洞
- `NEUTRAL`：补充遗漏视角

### 风控 Agent 特殊权重

风控 Agent（`role = "risk"`）的 OPPOSE 意见被特别标记，在裁判裁决阶段会优先被采纳，避免系统过度乐观地给出买入信号。

## ReActAgent（ReAct 推理 Agent）

ReAct（Reasoning + Acting）模式将推理与工具调用交替进行：

```
Thought → Action → Observation → Thought → Action → ... → Final Answer
```

### 工具注册（ToolRegistry）

Agent 通过注册工具来获取外部数据：

| 工具名 | 功能 |
|---|---|
| `get_stock_data` | 获取历史 K 线数据 |
| `calculate_technical_indicators` | 计算技术指标 |
| `get_news` | 获取相关新闻 |
| `get_market_overview` | 获取大盘概况 |

工具调用通过 `LlmToolAdapter` 适配为 OpenAI Function Calling 格式，LLM 决定何时调用哪个工具。

## 结构化推理链

### 六步法框架

推理链通过 `StructuredReasoningPromptBuilder` 注入到 Prompt 中：

**单维度六步法（各专业 Agent 使用）：**

```
Step 1 观察（Observation）：直接陈述观察到的数据事实
Step 2 判断（Judgment）：基于观察做出初步判断
Step 3 假设（Hypothesis）：提出可能的解释假设
Step 4 验证（Verification）：用至少 2 个数据证据验证假设
Step 5 风险（Risk）：提出至少 1 个反面论点（不利因素）
Step 6 结论（Conclusion）：综合以上给出明确结论
```

**多维度综合六步法（MultiAgentOrchestrator 综合时使用）：**

```
Step 1 汇总（Summary）：汇总各维度关键结论
Step 2 一致性（Consistency）：检查各维度结论是否一致
Step 3 权衡（Tradeoff）：分析一致与矛盾之处的权重
Step 4 综合（Synthesis）：综合形成整体判断
Step 5 风险（Risk）：识别最主要的下行风险
Step 6 决策（Decision）：给出明确的交易信号与置信度
```

### Few-shot 推理模板

`FewShotReasoningTemplateBuilder` 从 `ExperienceMemory` 中检索历史成功案例，注入到 Prompt 中让 LLM 学习：

```
[历史成功案例 - 相似市场条件下的正确判断]
股票: XXX, 市场阶段: 上涨趋势
观察: MACD 金叉 + 量价齐升
结论: 买入，后续 5 天涨幅 8%

[你需要分析的股票]
请按照以上格式进行分析...
```

## 技能系统（Skills）

技能是预定义的分析流程模板，存储在 `resources/skills/`：

| 技能目录 | 对应场景 |
|---|---|
| `skills/stock_analysis/` | 单股深度分析 |
| `skills/backtest/` | 策略回测分析 |
| `skills/alert/` | 告警触发分析 |
| `skills/intelligence/` | 市场情报分析 |
| `skills/market_overview/` | 大盘概况分析 |
| `skills/portfolio/` | 投资组合分析 |

技能通过 `SkillsLoader` 在启动时加载，`SkillsInstaller` 处理技能的安装与更新。

## 信号闭环学习

Agent 系统与信号评估形成完整的自学习闭环：

```
Agent 生成信号（DecisionSignal）
    ↓
5/10/20 天后 SignalOutcomeEvaluator 评估准确率
    ↓
准确率数据写入 ExperienceMemory
    ↓
下次分析时，SignalLearningService 从 ExperienceMemory 检索
    ├── 按条件相似度查找历史经验
    └── 识别错误模式（如：横盘期不适合动量策略）
    ↓
将 Few-shot 经验注入 Prompt
    ↓
Agent 从历史错误中学习，产出更高质量的新信号
```

## 系统评估基准

`BenchmarkSuite` 提供四大接口对 Agent 输出进行质量评估：

### 策略质量评分（StrategyQualityScorer）

六维度加权评分，总分 100，等级 A/B/C/D：

| 维度 | 权重 | 说明 |
|---|---|---|
| 收益能力 | 25% | 总收益率、年化收益率 |
| 风险控制 | 25% | 最大回撤、夏普比率 |
| 胜率质量 | 15% | 胜率、盈亏比 |
| 稳健性 | 15% | 不同时期表现一致性 |
| 成本效率 | 10% | 手续费占收益比例 |
| 一致性 | 10% | 信号与结果的一致性 |

### LLM 分析质量评估（LlmAnalysisQualityAssessor）

五维度规则引擎：

| 维度 | 说明 |
|---|---|
| 数据准确性 | 报告中的数据与源数据交叉验证 |
| 逻辑一致性 | 分析逻辑是否自洽 |
| 完整性 | 是否涵盖技术面、基本面、风险 |
| 可操作性 | 是否给出明确的入场价、止损价、目标价 |
| 风险披露 | 是否提及潜在风险和不利因素 |

**幻觉检测**：将 LLM 报告中提及的数值与上下文实际数据对比，识别捏造数据。
