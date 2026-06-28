# 股票智能分析系统 (Java 版)

AI 驱动的股票分析平台：多维度分析、YAML 策略引擎、回测、选股、组合管理与多渠道通知。

## 环境要求

- JDK 17+
- Maven 3.8+

## 快速开始

```bash
# 1. 复制配置
cp .env.example .env
# 编辑 .env，至少配置 LLM_API_KEY

# 2. 编译
mvn clean package -DskipTests

# 3. 启动 Web 服务（默认端口 8000）
java -jar target/daily-alphaforge-analysis-1.0.0.jar --serve

# 或单次分析
java -jar target/daily-alphaforge-analysis-1.0.0.jar --stocks 600519
```

## 运行模式

| 参数 | 说明 |
|------|------|
| `--serve` | Web API + 分析（默认） |
| `--serve-only` | 仅 API |
| `--schedule` | 定时分析 |
| `--stocks CODE,...` | 单次分析 |
| `--backtest` | 回测模式 |
| `--market-review` | 大盘复盘 |

## 数据目录

运行时数据位于 `~/.j_daily-stock-analysis/`：

- `data/stock_analysis.db` — SQLite 数据库
- `logs/` — 日志文件

## API

- 健康检查：`GET /api/v1/health`
- 分析：`POST /api/v1/analysis/run`
- 策略列表：`GET /api/v1/strategies`
- 回测：`POST /api/v1/backtest/run`
- 选股：`POST /api/v1/screening/run`

## Docker

```bash
mvn clean package -DskipTests
docker build -t daily-alphaforge-analysis .
docker run -p 8000:8000 --env-file .env daily-alphaforge-analysis
```

## 配置说明

详见 `.env.example`。关键项：

- `LLM_API_KEY` — LLM API 密钥（必填）
- `STOCK_LIST` — 默认分析股票列表
- `AUTH_ENABLED` — 启用 JWT 认证
- `NOTIFICATION_CHANNELS` — 通知渠道
