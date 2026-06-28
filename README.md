# AlphaForge

> **锻造超额收益** — AI 驱动的股票智能分析系统

AlphaForge 是一个基于 Java 17 + Spring Boot 3.2 构建的全栈量化分析平台，集成 LLM（大语言模型）进行多维度智能研判，覆盖 **技术分析、基本面分析、舆情分析、策略回测、智能选股、模拟交易、告警监控** 等完整投研闭环。

---

## 目录

- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [运行模式](#运行模式)
- [配置说明](#配置说明)
- [API 接口](#api-接口)
- [策略引擎](#策略引擎)
- [项目结构](#项目结构)
- [Docker 部署](#docker-部署)
- [开发指南](#开发指南)

---

## 核心特性

### 🤖 AI 智能分析
- 多 Agent 协作 / 单 Agent / 编排模式，灵活选择分析深度
- LLM 驱动的技术面、基本面、舆情三维综合研判
- 结构化决策信号输出（含入场区间、止损、目标价、置信度）
- AI 对话式交互，自然语言驱动分析任务

### 📊 策略回测引擎
- 19 种内置量化策略（趋势跟踪、动量、均值回归、形态、事件驱动等）
- 三大引擎：`BacktestSignalEngine`（历史回测）/ `ScreeningScoreEngine`（选股）/ `CompositeScoringEngine`（综合评分）
- 高级分析：参数优化、Walk-Forward 验证、蒙特卡洛模拟、组合回测
- 策略复盘与自动衰减机制

### 📈 智能选股（AlphaSift）
- 全市场扫描（A 股 / 港股 / 美股）
- 多因子综合排名、事件驱动筛选
- 批量深度分析队列

### 💼 投资组合管理
- 多账户持仓跟踪（A 股 / 港股 / 美股）
- 交易记录、资金流水、公司行动管理
- 持仓分布与收益曲线可视化

### 📝 模拟交易
- 实时行情模拟下单，零风险策略验证
- 多账户管理、融资操作模拟
- 自动结算资金与持仓

### 🔔 告警系统
- 多类型告警：价格突破/跌破、成交量异动、均线交叉、涨跌幅超限
- 多渠道推送：企业微信、飞书、钉钉、邮件、Bark、Webhook
- 告警规则管理、触发记录、通知日志追踪

### 🔄 Loop 自我感知
- 系统健康度监控与自动降级
- 信号准确率追踪与策略权重动态调优
- Verifier 层信号校验

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ REST API │  │ WebSocket│  │ Scheduler│  │  Bot   │ │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘ │
├─────────────────────────────────────────────────────────┤
│                    Application Layer                      │
│  ┌────────┐  ┌──────────┐  ┌────────┐  ┌───────────┐  │
│  │Pipeline│  │  Agent   │  │Backtest│  │  Strategy │  │
│  └────────┘  └──────────┘  └────────┘  └───────────┘  │
│  ┌────────────────┐  ┌───────────────────────────────┐  │
│  │    Services    │  │     Skills (Prompt-driven)    │  │
│  └────────────────┘  └───────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│                      Domain Layer                         │
│  ┌──────────────────┐  ┌────────────────────────────┐   │
│  │   Domain Models  │  │    Domain Services         │   │
│  └──────────────────┘  └────────────────────────────┘   │
├─────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                    │
│  ┌────────┐ ┌─────┐ ┌────────────┐ ┌──────┐ ┌──────┐  │
│  │DataProv│ │ LLM │ │Notification│ │SQLite│ │Search│  │
│  └────────┘ └─────┘ └────────────┘ └──────┘ └──────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| 数据库 | SQLite (嵌入式) | 3.45.3 |
| ORM | MyBatis | 3.0.3 |
| HTTP 客户端 | OkHttp | 4.12.0 |
| JSON | Jackson | 2.17.0 |
| 技术分析 | TA-Lib (Java Binding) | 0.4.0 |
| 认证 | JJWT | 0.12.5 |
| 实时推送 | Spring WebSocket | - |
| 模板 | FreeMarker + Thymeleaf | - |
| 工具 | Guava / Commons Lang3 / Lombok | - |
| 容器 | Docker (eclipse-temurin:17-jre-alpine) | - |

---

## 快速开始

### 前提条件

- JDK 17+
- Maven 3.8+
- （可选）Docker & Docker Compose

### 1. 克隆项目

```bash
git clone https://github.com/your-org/AlphaForge.git
cd AlphaForge
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，至少填入 LLM_API_KEY
```

**最小必要配置：**
```properties
LLM_API=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=sk-your-api-key
```

### 3. 构建 & 运行

```bash
# 编译
mvn clean package -DskipTests

# 启动 Web 服务（默认模式）
java -jar target/daily-stock-analysis-1.0.0.jar --serve

# 访问 Web UI
open http://localhost:8000
```

---

## 运行模式

AlphaForge 支持多种运行模式，通过命令行参数切换：

| 模式 | 命令 | 说明 |
|------|------|------|
| Web 服务 | `--serve` | 启动完整 Web API + 前端界面（默认） |
| 纯 API | `--serve-only` | 仅启动 REST API，不触发分析 |
| 单次分析 | `--stocks 600519,300750` | 对指定股票执行一次性分析 |
| 定时调度 | `--schedule` | 按 Cron 表达式自动执行分析 |
| 大盘复盘 | `--market-review` | 执行全市场复盘分析 |
| 策略回测 | `--backtest --stocks 600519 --backtest-days 60` | 对指定股票回测 |

**辅助参数：**
- `--dry-run`：试运行，不写入数据库
- `--debug`：开启详细调试日志

---

## 配置说明

所有配置通过 `.env` 文件管理（基于 dotenv-java），主要分组：

| 分组 | 关键配置项 | 说明 |
|------|-----------|------|
| **LLM** | `LLM_API`, `LLM_API_KEY`, `LLM_MODEL` | AI 分析核心，支持 OpenAI 兼容 API |
| **数据源** | `DATA_PROVIDER`, `TUSHARE_TOKEN` | 支持 efinance/akshare/tushare/tencent/yfinance |
| **通知** | `NOTIFICATION_CHANNELS`, `WECOM_WEBHOOK` | 企业微信/飞书/钉钉/邮件/Bark |
| **认证** | `AUTH_ENABLED`, `AUTH_SECRET` | JWT 认证（默认关闭） |
| **调度** | `SCHEDULE_CRON` | 默认工作日 18:00 |
| **服务** | `SERVER_PORT`, `SERVER_HOST` | 默认 8000 端口 |

详见 [.env.example](.env.example)。

---

## API 接口

所有 API 前缀：`/api/v1`

| 模块 | 路径 | 说明 |
|------|------|------|
| 系统 | `/health`, `/dashboard` | 健康检查、仪表盘数据 |
| 分析 | `/analysis/**` | 触发分析、查看报告 |
| 信号 | `/signals/**` | 决策信号 CRUD、反馈 |
| 选股 | `/screening/**` | AlphaSift 智能选股 |
| 回测 | `/backtest/**` | 策略回测、高级分析 |
| 策略 | `/strategies/**` | 策略目录、复盘报告 |
| 组合 | `/portfolio/**` | 持仓、交易、资金流水 |
| 模拟交易 | `/paper-trading/**` | 模拟账户、下单 |
| 自选股 | `/watchlist/**` | 关注列表管理 |
| 告警 | `/alerts/**` | 规则、触发、通知 |
| 对话 | `/chat/**` | AI 对话会话管理 |
| 认证 | `/auth/**` | 登录、Token 刷新 |

---

## 策略引擎

### 三大引擎

| 引擎 | 职责 | 策略数 |
|------|------|--------|
| `BacktestSignalEngine` | 历史 K 线回测，生成买卖点 | 10 |
| `ScreeningScoreEngine` | 全市场实时选股打分 | 4 |
| `CompositeScoringEngine` | 多策略加权综合评分 | 15 |

### 内置策略（19 种）

| 分类 | 策略 |
|------|------|
| 趋势跟踪 | 均线金叉 (ma_golden_cross)、多头排列 (bull_trend)、波浪理论 (wave_theory) |
| 动量 | 动量策略 (momentum)、情绪周期 (emotion_cycle)、龙头战法 (dragon_head) |
| 回调 | 缩量回调 (shrink_pullback)、箱体震荡 (box_oscillation) |
| 形态 | 放量突破 (volume_breakout)、底部放量 (bottom_volume)、一阳穿三阴 (one_yang_three_yin) |
| 价值 | 价值成长 (value_growth)、成长质量 (growth_quality)、高股息 (dividend)、双低策略 (dual_low) |
| 事件 | 事件驱动 (event_driven)、预期重定价 (expectation_repricing)、热点题材 (hot_theme) |
| 其他 | 缠论 (chan_theory) |

策略定义文件位于 `src/main/resources/strategies/definitions/`，支持 YAML 声明式配置。

---

## 项目结构

```
src/main/java/io/leavesfly/alphaforge/
├── application/                   # 应用层
│   ├── agent/                     # AI Agent（ReAct 模式）
│   │   ├── skills/                # Agent 技能（Prompt 模板）
│   │   └── tools/                 # Agent 工具集
│   ├── backtest/                  # 回测模拟器
│   ├── pipeline/                  # 分析流水线（编排）
│   ├── service/                   # 应用服务
│   │   ├── portfolio/             # 组合管理
│   │   ├── report/                # 报告生成
│   │   ├── screening/             # 智能选股
│   │   └── signal/                # 信号管理
│   └── strategy/                  # 策略引擎
├── config/                        # 配置类
├── domain/                        # 领域层
│   ├── model/                     # 领域模型
│   └── service/                   # 领域服务
├── infrastructure/                # 基础设施层
│   ├── dataprovider/              # 行情数据源
│   ├── llm/                       # LLM 调用与用量追踪
│   ├── notification/              # 通知渠道
│   ├── persistence/               # 数据持久化（MyBatis）
│   ├── search/                    # 搜索引擎（Tavily/Anspire）
│   └── storage/                   # 文件存储
├── presentation/                  # 表现层
│   ├── api/                       # REST Controller
│   ├── bot/                       # Bot 接入
│   └── scheduler/                 # 定时任务
└── util/                          # 工具类
```

---

## Docker 部署

### 使用 Docker Compose（推荐）

```bash
# 构建 JAR
mvn clean package -DskipTests

# 启动容器
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### 单独 Docker 构建

```bash
docker build -t alphaforge:latest .
docker run -d --name alphaforge \
  -p 8000:8000 \
  -v ./data:/app/data \
  -v ./logs:/app/logs \
  --env-file .env \
  alphaforge:latest
```

**容器配置：**
- 基础镜像：`eclipse-temurin:17-jre-alpine`
- JVM 参数：`-Xms256m -Xmx512m`
- 健康检查：`GET /api/v1/health`（30s 间隔）
- 数据持久化：`/app/data`（SQLite 数据库）
- 日志目录：`/app/logs`

---

## 开发指南

### 本地开发

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动（开发模式）
mvn spring-boot:run -Dspring-boot.run.arguments="--serve"
```

### 添加新策略

1. 在 `src/main/resources/strategies/definitions/` 创建 `your_strategy.yaml`
2. 在 `catalog.yaml` 中注册策略条目
3. 声明能力（backtest / screening / scoring）
4. 引擎会自动加载并执行策略条件

### 添加新数据源

在 `infrastructure/dataprovider/` 下实现数据获取接口，注册到 `DataFetcherManager`。

### 添加新通知渠道

在 `infrastructure/notification/` 下实现通知发送逻辑，注册到 `NotificationRouter`。

---

## License

MIT License

---

## 联系

如有问题或建议，欢迎提交 Issue 或 Pull Request。
