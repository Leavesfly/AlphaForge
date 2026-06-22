# 股票智能分析系统 - Java版

> 基于 Spring Boot + Maven 技术栈，100% 还原 Python 版 daily_stock_analysis 全部功能。

## 快速开始

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 LLM_API_KEY 和 STOCK_LIST

# 2. 编译打包
mvn clean package -DskipTests

# 3. 运行
java -jar target/daily-stock-analysis-1.0.0.jar

# 4. 访问Web界面
# http://localhost:8000/web/dashboard
```

## Docker部署

```bash
docker-compose up -d
```

## 运行模式

| 模式 | 命令 | 说明 |
|------|------|------|
| Web服务 | `--serve` | 启动API+WebUI(默认) |
| 单次分析 | `--stocks 600519,AAPL` | 分析指定股票 |
| 定时调度 | `--schedule` | 工作日自动分析 |
| 大盘复盘 | `--market-review` | 主要指数分析 |
| 干运行 | `--dry-run` | 不调LLM和通知 |

## 项目结构

```
src/main/java/com/stock/
├── Application.java          # 主入口
├── config/                   # 配置管理
├── core/                     # 核心流水线(Pipeline/TradingCalendar/ContextBuilder)
├── dataprovider/             # 11个数据源适配器(含熔断器)
├── llm/                      # LLM服务(多渠道+Token追踪)
├── agent/                    # Agent系统(6Agent+Executor+Memory+Skills)
├── service/                  # 业务服务层(15个服务)
├── api/controller/           # REST API(14个端点)
├── bot/                      # Bot系统(飞书/钉钉/Telegram)
├── notification/             # 通知系统(13渠道+路由+降噪)
├── model/                    # 数据模型(Entity/Enum/Schema)
├── repository/               # 数据访问层(5个Repository)
├── scheduler/                # 定时调度
├── web/                      # WebUI页面控制器
└── util/                     # 工具类
src/main/resources/
├── templates/                # Thymeleaf页面模板(8页)
├── static/                   # CSS/JS静态资源
├── strategies/               # 交易策略YAML(10个策略)
└── application.yml           # Spring Boot配置
```

## 技术栈

- Java 17 + Spring Boot 3.2.5
- SQLite + JPA/Hibernate
- OkHttp (HTTP客户端)
- Thymeleaf (服务端渲染)
- Jackson (JSON/YAML处理)
- JWT认证

## 数据源(11个全覆盖)

| 数据源 | 市场 | 说明 |
|--------|------|------|
| EFinance | A股 | 东方财富接口(免费,最高优先) |
| AkShare | A股 | 东方财富/证券之星 |
| Tushare | A股 | Tushare Pro(需Token) |
| Tencent | A股/港股 | 腾讯财经 |
| BaoStock | A股 | 新浪财经(等效替代) |
| PytdxFetcher | A股 | 通达信协议(TCP+HTTP降级) |
| YFinance | 美股 | Yahoo Finance |
| Finnhub | 美股/全球 | Finnhub API |
| AlphaVantage | 美股/全球 | Alpha Vantage |
| Longbridge | 港股/美股 | 长桥OpenAPI |
| TickFlow | A股 | TickFlow API |

## API端点

- `GET /api/v1/health` - 健康检查
- `POST /api/v1/analysis/run` - 触发分析
- `GET /api/v1/history` - 分析历史
- `GET /api/v1/stocks/{code}/quote` - 实时行情
- `GET /api/v1/portfolio` - 投资组合
- `GET /api/v1/alerts` - 告警列表
- `POST /api/v1/backtest/run` - 策略回测
- `GET /api/v1/decision-signals` - 决策信号
- `POST /api/v1/agent/chat` - AI问股
- `GET /api/v1/intelligence/{code}` - 智能情报
- `GET /api/v1/system-config` - 系统配置
- `GET /api/v1/usage` - 用量统计

## Web页面

访问 `http://localhost:8000/web/` 包含:
- 仪表盘(大盘情绪+最近分析+组合概览)
- 股票分析(输入代码→触发→查看报告)
- 投资组合(持仓管理+盈亏+风险评估)
- 告警管理(条件监控+自动推送)
- 策略回测(选策略→运行→绩效可视化)
- AI问股(实时对话)
- 分析历史
- 系统设置
