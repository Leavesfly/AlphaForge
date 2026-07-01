# AlphaForge 项目总览

## 项目定位

**AlphaForge**（原名 daily-stock-analysis）是一套面向个人投资者和量化研究者的**股票智能分析系统**，目标是「锻造超额收益」。系统集成了 AI 大语言模型（LLM）、多 Agent 协作、因子自进化、策略回测等能力，通过数据驱动的方式辅助投资决策。

## 核心能力一览

| 能力模块 | 描述 |
|---|---|
| **AI 多维度分析** | 技术面 + 基本面 + 情绪面三路 Agent 并行分析，LLM 综合裁决 |
| **Agent 辩论机制** | 三轮制辩论（独立分析 → 交叉质询 → 裁判裁决），提升信号可靠性 |
| **结构化推理链** | 六步推理框架（观察→判断→假设→验证→风险→结论），强制证据支撑 |
| **策略回测** | 真实感回测仿真（T+1、涨跌停、手续费、滑点），支持参数优化 |
| **因子自进化** | LLM 驱动的因子自动发现与遗传进化，IC/IR 量化评估筛选 |
| **智能选股 AlphaSift** | 全市场扫描，多策略打分筛选优质标的 |
| **信号闭环学习** | 信号发出 → 事后评估 → 经验积累 → Few-shot 注入 → 分析改进 |
| **投资组合管理** | 多账户模拟交易，持仓跟踪、盈亏统计、公司行动处理 |
| **告警系统** | 价格/技术指标/事件多维度告警，支持邮件、Webhook 等多渠道推送 |
| **AI 对话** | 多轮会话，结合 ReAct Agent 实现工具调用 |
| **系统评估基准** | 六维策略质量评分（ABCD 等级）+ LLM 分析幻觉检测 |

## 技术栈

| 类别 | 选型 |
|---|---|
| 运行时 | Java 17 |
| 核心框架 | Spring Boot 3.2.5 |
| 数据层 | SQLite + MyBatis 3.0.3 |
| HTTP | OkHttp 4.12.0 |
| JSON | Jackson 2.17.0 |
| 技术指标 | TA-Lib Java Binding 0.4.0 |
| 安全 | JJWT 0.12.5（JWT 认证） |
| 工具 | Guava 33.1、Apache Commons Lang3 |
| 构建 | Maven（父 POM: spring-boot-starter-parent 3.2.5） |

## 项目目录结构

```
AlphaForge/
├── src/main/java/io/leavesfly/alphaforge/
│   ├── application/          # 应用层：业务逻辑编排
│   │   ├── agent/            # Agent 系统（多Agent、辩论、推理链）
│   │   ├── backtest/         # 回测仿真器
│   │   ├── evaluation/       # 评估基准
│   │   ├── factor/evolution/ # 因子自进化系统
│   │   ├── pipeline/         # 股票分析流水线
│   │   ├── service/          # 各业务服务
│   │   └── strategy/         # 策略引擎
│   ├── domain/               # 领域层：核心业务模型与接口
│   │   ├── model/entity/     # 实体类
│   │   ├── repository/       # 仓储接口
│   │   └── service/          # 领域服务与端口
│   ├── infrastructure/       # 基础设施层：外部系统集成
│   │   ├── dataprovider/     # 市场数据获取
│   │   ├── llm/              # LLM 服务
│   │   ├── memory/           # 向量记忆存储
│   │   └── notification/     # 通知服务
│   ├── presentation/         # 表现层：API 控制器、Bot、调度器
│   └── config/               # 配置类
├── src/main/resources/
│   ├── strategies/           # 策略 YAML 定义文件
│   ├── prompts/              # LLM Prompt 模板
│   ├── skills/               # 技能配置文件
│   ├── mapper/               # MyBatis Mapper XML
│   └── schema.sql            # 数据库建表 SQL
└── wiki/                     # 本技术文档目录
```

## 数据流概览

```
用户请求
    ↓
REST API (AnalysisController)
    ↓
TaskService（异步任务提交）
    ↓
StockAnalysisPipeline（30+ 步骤编排）
    ├── AnalysisContextBuilder   → 构建分析上下文（K线、技术指标）
    ├── AnalysisContextEnhancer  → 增强上下文（筹码、板块、新闻、大盘）
    ├── AgentAnalysisService     → Agent 模式分析
    │   ├── debate 模式          → AgentDebateOrchestrator（三轮辩论）
    │   ├── multi 模式           → MultiAgentOrchestrator（并行分析）
    │   ├── react 模式           → ReActAgent（工具调用）
    │   └── llm 模式（降级）      → 直接 LLM 调用
    ├── AnalysisResultAggregator → 聚合多维分析结果
    └── AnalysisPostProcessor    → 后处理（信号提取、通知、存储）
            ↓
    DecisionSignal（结构化交易信号）
            ↓
    SignalOutcomeEvaluator（事后评估）
            ↓
    ExperienceMemory（经验积累）→ 下次分析注入 Few-shot
```

## 运行时数据目录

系统运行时数据统一存放在 `~/.alphaforge/`：

```
~/.alphaforge/
├── data/
│   └── stock_analysis.db   # SQLite 主数据库
├── cache/                  # 数据缓存
├── reports/                # 分析报告导出
└── logs/                   # 运行日志
```

## 文档导航

| 文档 | 描述 |
|---|---|
| [architecture.md](architecture.md) | 系统分层架构与模块职责 |
| [agent-system.md](agent-system.md) | Agent 系统、辩论机制、推理链 |
| [strategy-system.md](strategy-system.md) | 策略定义、引擎与四维分类 |
| [backtest-system.md](backtest-system.md) | 回测仿真器实现细节 |
| [factor-evolution.md](factor-evolution.md) | 因子自进化架构（FactorMiner/AlphaAgentEvo） |
| [data-model.md](data-model.md) | 数据库表结构与领域模型 |
| [api-reference.md](api-reference.md) | REST API 接口参考 |
| [deployment.md](deployment.md) | 部署、配置与环境变量 |
