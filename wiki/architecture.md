# 系统分层架构

## 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer（表现层）              │
│  REST API Controllers │ WebSocket │ Telegram Bot │ Scheduler │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Application Layer（应用层）                │
│  StockAnalysisPipeline │ MultiAgentOrchestrator              │
│  AgentDebateOrchestrator │ FactorEvolutionOrchestrator       │
│  BacktestSimulator │ AlphaSiftScreeningEngine                │
│  各类 Service（Alert、Chat、Portfolio、Signal 等）             │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                     Domain Layer（领域层）                    │
│  实体模型（Entity）│ 仓储接口（Repository Port）               │
│  领域服务（TechnicalAnalysisService、NameToCodeResolver 等）  │
│  端口定义（LlmPort、MarketDataPort、NotificationPort）        │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                 Infrastructure Layer（基础设施层）            │
│  DataFetcherManager │ LlmService │ NotificationService       │
│  MyBatis Mapper │ EmbeddingService │ SignalQualityPredictor   │
└─────────────────────────────────────────────────────────────┘
```

## 各层职责详述

### 1. 表现层（presentation）

| 包路径 | 职责 |
|---|---|
| `presentation/api/` | RESTful HTTP 接口，接收客户端请求，委托应用层处理 |
| `presentation/bot/` | Telegram Bot 交互（命令解析、消息推送） |
| `presentation/scheduler/` | Spring 定时任务（定时分析、告警检查、信号评估） |

**主要 Controller 列表：**

| 类名 | 路径前缀 | 说明 |
|---|---|---|
| `AnalysisController` | `/api/v1` | 股票分析、历史记录、行情查询 |
| `BacktestController` | `/api/v1/backtest` | 策略回测、参数优化、蒙特卡洛模拟 |
| `PortfolioController` | `/api/v1/portfolio` | 投资组合查询 |
| `PaperTradingController` | `/api/v1/paper-trading` | 模拟交易（账户、买卖、持仓） |
| `SignalController` | `/api/v1/signal` | 决策信号管理 |
| `ScreeningController` | `/api/v1/screening` | 智能选股（AlphaSift） |
| `AlertController` | `/api/v1/alert` | 告警规则管理 |
| `ChatController` | `/api/v1/chat` | AI 对话会话 |
| `StrategyController` | `/api/v1/strategy` | 策略目录查询 |
| `WatchlistController` | `/api/v1/watchlist` | 自选股管理 |
| `SystemController` | `/api/v1/system` | 健康检查、用量统计、仪表盘 |
| `AuthController` | `/api/v1/auth` | JWT 认证 |
| `AiCapabilityController` | `/api/v1/ai` | AI 能力演示页面 |

### 2. 应用层（application）

应用层是系统的核心编排层，不包含业务规则，只负责把领域服务、基础设施能力按业务流程串联起来。

#### 2.1 流水线模块（pipeline）

`StockAnalysisPipeline` 是系统的核心编排器，执行完整的 30+ 步骤分析流程：

```
runFullAnalysis()
  1. 解析股票列表
  2. 获取历史 K 线数据（DataFetcherManager）
  3. 技术指标计算（TechnicalAnalysisService）
  4. 上下文构建（AnalysisContextBuilder）
  5. 上下文增强（AnalysisContextEnhancer）
     ├── 筹码结构分析
     ├── 板块关联查询
     ├── 新闻情报检索
     └── 大盘环境评估
  6. 信号学习注入（SignalLearningService → Few-shot）
  7. Agent 分析（AgentAnalysisService，含降级链）
  8. 结果聚合（AnalysisResultAggregator）
  9. 结果后处理（AnalysisPostProcessor）
     ├── 信号提取（SignalExtractionService）
     ├── 报告存储（AnalysisHistoryService）
     └── 通知推送（NotificationPort）
 10. 诊断记录（DiagnosticContext）
```

**关键 Pipeline 组件：**

| 类名 | 职责 |
|---|---|
| `AnalysisContextBuilder` | 从 K 线和技术指标构建标准化分析上下文 |
| `AnalysisContextEnhancer` | 注入筹码、板块、新闻、大盘信息 |
| `AgentAnalysisService` | 封装四级降级链（debate→multi→react→llm） |
| `AnalysisResultAggregator` | 聚合多维度分析结果，计算综合评分 |
| `AnalysisPostProcessor` | 后处理（信号提取、Fallback 补全、Dashboard 回填） |
| `PipelineMetrics` | 流水线运行指标收集 |
| `DiagnosticContext` | 运行时诊断快照（用于调试） |

#### 2.2 Agent 模块（agent）

详见 [agent-system.md](agent-system.md)。

#### 2.3 策略模块（strategy）

详见 [strategy-system.md](strategy-system.md)。

#### 2.4 回测模块（backtest）

详见 [backtest-system.md](backtest-system.md)。

#### 2.5 因子进化模块（factor/evolution）

详见 [factor-evolution.md](factor-evolution.md)。

#### 2.6 业务服务模块（service）

| 包名 | 核心类 | 职责 |
|---|---|---|
| `service/alert/` | `AlertService`、`AlertWorker` | 告警规则 CRUD、定时触发评估 |
| `service/chat/` | `ChatService` | 多轮对话、ReAct Agent 集成 |
| `service/portfolio/` | `PaperTradingService`、`PortfolioService` | 模拟交易、持仓管理 |
| `service/report/` | `AnalysisHistoryService`、`ReportFormatterService` | 历史报告查询、格式化 |
| `service/signal/` | `DecisionSignalService`、`SignalOutcomeEvaluator` | 信号管理、事后准确率评估 |
| `service/screening/` | `AlphaSiftScreeningEngine` | 全市场 AI 扫描选股 |
| `service/market/` | `MarketAnalysisService`、`NewsSearchService` | 大盘分析、新闻检索 |
| `service/task/` | `TaskService`、`SystemService` | 异步任务队列、系统健康 |
| `service/feedback/` | `SignalLearningService`、`ExperienceMemory` | 信号反馈、跨轮经验积累 |
| `service/memory/` | `AnalysisMemoryService` | 分析记忆存储 |

### 3. 领域层（domain）

领域层包含业务核心概念，不依赖任何框架实现。

#### 3.1 领域模型（model/entity）

| 包路径 | 实体类 | 说明 |
|---|---|---|
| `entity/analysis/` | `AnalysisReport`、`AnalysisTask`、`AnalysisResult` | 分析报告与任务 |
| `entity/signal/` | `DecisionSignal`、`DecisionSignalOutcome`、`DecisionSignalFeedback` | 交易信号 |
| `entity/portfolio/` | `PortfolioAccount`、`PortfolioPosition`、`PortfolioTrade`、`CashLedgerEntry`、`CorporateAction` | 投资组合 |
| `entity/market/` | `StockDailyData` | 日线行情数据 |
| `entity/alert/` | `AlertRule`、`AlertTrigger`、`AlertNotification` | 告警系统 |

#### 3.2 端口定义（service/port）

端口接口实现领域层与基础设施层的解耦（依赖倒置原则）：

| 接口 | 实现类 | 说明 |
|---|---|---|
| `LlmPort` | `LlmService` | LLM 调用抽象 |
| `MarketDataPort` | `DataFetcherManager` | 市场数据获取抽象 |
| `NotificationPort` | `NotificationService` | 消息推送抽象 |

#### 3.3 领域服务（service）

| 类名 | 说明 |
|---|---|
| `TechnicalAnalysisService` | 基于 TA-Lib 计算 MACD、KDJ、RSI、布林带等技术指标 |
| `NameToCodeResolver` | 股票名称→代码解析（支持模糊匹配） |
| `TradingCalendar` | 交易日历服务（判断是否为交易日，计算交易日区间） |
| `SignalVerifier` | 信号验证（格式、合理性检查） |

### 4. 基础设施层（infrastructure）

#### 4.1 数据提供层（dataprovider）

`DataFetcherManager` 是市场数据获取的核心，实现了以下能力：

- **多数据源自动切换**：AkShare、EFinance、Tushare、YFinance
- **故障熔断与自动恢复**：CircuitBreaker 模式，失败超阈值后熔断，窗口期后自动尝试恢复
- **防封禁流控**：RateLimiter + SlidingWindowRateLimiter，限制请求频率并叠加随机抖动
- **指数退避重试**：请求失败后按 2^n 指数间隔重试
- **交易日感知缓存**：区分交易日/非交易日，增量更新避免重复拉取
- **数据质量校验**：DataQualityValidator 检测缺失交易日、异常价格

#### 4.2 LLM 服务层（llm）

| 类名 | 职责 |
|---|---|
| `LlmService` | 统一 LLM 调用入口，实现 `LlmPort` |
| `LlmChannelManager` | 多渠道管理，支持 OpenAI/DashScope/Ollama 故障切换 |
| `LlmRequestBuilder` | 构建 Chat Completion 请求（含 Function Calling） |
| `LlmResponseParser` | 解析 LLM 响应（含流式 SSE、JSON 提取） |
| `LlmRetryExecutor` | 指数退避重试执行器 |
| `LlmUsageTracker` | Token 用量统计与费用追踪 |
| `LlmResponseCache` | 响应缓存（开发/测试环境可开启） |
| `LlmMetrics` | Micrometer 监控指标埋点 |

#### 4.3 通知层（notification）

| 类名 | 职责 |
|---|---|
| `NotificationService` | 通知分发入口，实现 `NotificationPort` |
| `NotificationRouter` | 根据渠道配置路由到具体 Sender |
| `sender/EmailSender` | 邮件（Spring Mail） |
| `sender/WebhookSender` | 企业微信 / Feishu Webhook |
| `sender/BarkSender` | iOS Bark 推送 |

## 配置模块（config）

| 配置类 | 说明 |
|---|---|
| `AppConfig` | 应用主配置（app.home、市场类型等） |
| `LlmConfig` | LLM 多渠道配置（API地址、密钥、模型名） |
| `DataProviderConfig` | 数据源优先级、限流参数 |
| `SchedulerAuthConfig` | 定时任务鉴权 + Agent 模式配置 |
| `ScoringConfig` | 评分维度权重 |
| `NotificationConfig` | 通知渠道开关与参数 |
| `SearchConfig` | 新闻搜索服务配置 |
| `BotConfig` | Telegram Bot Token |
| `InfrastructureConfig` | OkHttpClient、ObjectMapper 公共 Bean |
| `EnvVarProvider` | 环境变量加载（dotenv-java） |

## 依赖注入策略

系统采用 Spring 构造函数注入为主，字段注入（`@Autowired(required = false)`）为辅：

- **必须依赖**：通过构造函数注入，Spring 启动时强制检查
- **可选依赖**（如 `MultiAgentOrchestrator`、`AgentDebateOrchestrator`）：通过字段注入且 `required = false`，允许缺失时降级处理

这种设计保证了核心功能的可靠性，同时允许高级功能在配置不完整时优雅降级。
