# 部署与配置

## 运行环境要求

| 要求 | 规格 |
|---|---|
| Java 版本 | JDK 17+ |
| 内存 | 最低 512MB，推荐 1GB+ |
| 磁盘 | 最低 2GB（含历史行情数据缓存） |
| 操作系统 | Linux / macOS / Windows（均支持） |
| 网络 | 可访问 LLM API 端点 + A 股行情数据源 |

## 环境变量配置

所有敏感配置通过 `.env` 文件管理（基于 `dotenv-java`），项目根目录提供了 `.env.example` 模板：

```bash
cp .env.example .env
# 编辑 .env 填入真实配置
```

### 必填配置

```bash
# LLM 服务（至少配置一个渠道）
LLM_API=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxx
LLM_MODEL=qwen-max

# JWT 认证
JWT_SECRET=your-super-secret-key-at-least-32-chars
```

### 可选配置

```bash
# 服务监听地址
SERVER_PORT=8000
SERVER_HOST=0.0.0.0

# 数据根目录（默认 ~/.alphaforge）
APP_HOME=/custom/path/.alphaforge

# 多 LLM 渠道（故障切换）
LLM_API_2=https://api.openai.com/v1
LLM_API_KEY_2=sk-xxxxxxxxxxxxxxxxxxxxx
LLM_MODEL_2=gpt-4o

# Agent 模式（single/react/multi/debate）
AGENT_MODE=multi

# 数据源优先级（akshare/eastmoney/tushare/yfinance）
DATA_PROVIDER_PRIMARY=akshare
DATA_PROVIDER_FALLBACK=eastmoney

# Tushare Token（若使用 Tushare 数据源）
TUSHARE_TOKEN=your_tushare_token

# 邮件通知
MAIL_HOST=smtp.163.com
MAIL_PORT=465
MAIL_USERNAME=your@email.com
MAIL_PASSWORD=your_smtp_password
MAIL_TO=recipient@email.com

# Webhook 通知（企业微信/飞书）
WEBHOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx

# Bark 通知（iOS）
BARK_URL=https://api.day.app/your_device_key/

# Telegram Bot
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

## 本地开发启动

```bash
# 1. 克隆项目
git clone <repo-url> AlphaForge
cd AlphaForge

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 LLM_API_KEY 等必填项

# 3. 编译构建
mvn clean package -DskipTests

# 4. 启动服务
java -jar target/daily-stock-analysis-1.0.0.jar

# 或直接通过 Maven 启动
mvn spring-boot:run
```

服务启动后访问：`http://localhost:8000`

## Docker 部署

### 使用 Dockerfile

```bash
# 构建镜像
docker build -t alphaforge:latest .

# 启动容器
docker run -d \
  --name alphaforge \
  -p 8000:8000 \
  -v ~/.alphaforge:/root/.alphaforge \
  --env-file .env \
  alphaforge:latest
```

### 使用 Docker Compose

```bash
docker-compose up -d
```

`docker-compose.yml` 中已配置：
- 端口映射：8000:8000
- 数据卷挂载：`~/.alphaforge` → `/root/.alphaforge`
- 环境变量文件：`.env`
- 基础镜像：`eclipse-temurin:17-jre-alpine`

### 容器内数据目录

容器内运行时数据目录为 `/root/.alphaforge/`，建议挂载到宿主机持久化存储：

```yaml
volumes:
  - ${HOME}/.alphaforge:/root/.alphaforge
```

## application.yml 关键配置说明

```yaml
server:
  port: ${SERVER_PORT:8000}           # 可通过环境变量覆盖
  shutdown: graceful                  # 优雅停机

spring:
  datasource:
    url: jdbc:sqlite:${app.home}/data/stock_analysis.db
    druid:
      max-active: 5                   # SQLite 文件锁，无需大连接池

  lifecycle:
    timeout-per-shutdown-phase: 3s    # 加速关闭

mybatis:
  configuration:
    map-underscore-to-camel-case: true  # 自动驼峰映射

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

## LLM 多渠道配置

支持 OpenAI 兼容格式的任意 LLM 提供商，通过配置多渠道实现故障切换：

```yaml
# LlmConfig 配置示例（对应 .env 变量）
llm:
  channels:
    - name: primary
      api: ${LLM_API}
      api_key: ${LLM_API_KEY}
      model: ${LLM_MODEL:qwen-max}
      priority: 1
    - name: fallback
      api: ${LLM_API_2:}
      api_key: ${LLM_API_KEY_2:}
      model: ${LLM_MODEL_2:gpt-4o}
      priority: 2
```

`LlmChannelManager` 按 `priority` 排序，主渠道失败后自动切换备用渠道。

## 数据源配置

```yaml
# DataProviderConfig 配置
data_provider:
  primary: ${DATA_PROVIDER_PRIMARY:akshare}
  fallback: ${DATA_PROVIDER_FALLBACK:eastmoney}
  rate_limit:
    akshare: 2000ms    # 每次请求最短间隔
    eastmoney: 1000ms
  circuit_breaker:
    failure_threshold: 3      # 连续失败 3 次触发熔断
    recovery_timeout: 60s     # 熔断后 60 秒尝试恢复
```

**支持的数据源：**

| 数据源 | 标识 | 说明 |
|---|---|---|
| AkShare | `akshare` | 开源行情库，免费，需 Python 环境 |
| EastMoney | `eastmoney` | 东方财富公开接口，免费 |
| Tushare | `tushare` | 需注册并获取 Token |
| Yahoo Finance | `yfinance` | 美股/港股，国内访问可能需代理 |

## 日志配置

日志配置文件：`src/main/resources/logback.xml`

```bash
# 日志文件路径（默认）
~/.alphaforge/logs/alphaforge.log

# 调整日志级别（通过 application.yml）
logging:
  level:
    root: INFO
    io.leavesfly.alphaforge: DEBUG    # 开发调试时开启
```

## 性能调优建议

| 场景 | 建议 |
|---|---|
| LLM 调用慢 | 开启 `LlmResponseCache`（开发/测试环境），或选择响应更快的模型 |
| 全市场选股耗时长 | 缩小 `AlphaSift` 的扫描 Universe 范围 |
| 回测数据拉取慢 | 预先拉取并缓存历史行情，或减小回测时间区间 |
| 内存占用高 | 减小 `druid.max-active` 连接数，调低 Agent 并发线程数 |
| 因子进化耗时 | 减小评估 Universe 股票数量，缩短 IC 计算的前瞻天数 |

## 定时任务

系统内置以下定时任务（`presentation/scheduler/`）：

| 任务 | 触发时间（默认） | 说明 |
|---|---|---|
| 定时分析 | 工作日 15:30 | 对自选股执行批量分析 |
| 告警检查 | 每 5 分钟 | 检查告警规则是否触发 |
| 信号评估 | 每日 20:00 | 对 5/10/20 天前的信号进行事后评估 |

定时任务可通过 `SchedulerAuthConfig` 配置的 Cron 表达式调整。
