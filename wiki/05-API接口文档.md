# API 接口文档

## 概述

所有 API 均以 `/api/v1` 为前缀，返回 JSON 格式数据。

**Base URL**: `http://localhost:8000/api/v1`

## 认证

当 `AUTH_ENABLED=true` 时，除 `/health` 和 `/auth/login` 外的所有接口需携带 JWT Token:

```
Authorization: Bearer <token>
```

---

## 健康检查与系统信息

### GET /health

健康检查接口。

**响应示例:**
```json
{
  "status": "healthy",
  "timestamp": "2024-01-01T10:00:00",
  "uptime": "3600s",
  "version": "1.0.0"
}
```

### GET /system

获取系统运行信息。

**响应示例:**
```json
{
  "java_version": "17.0.10",
  "os": "Linux",
  "market": "A",
  "data_provider": "efinance",
  "llm_model": "gpt-4o",
  "agent_mode": "true",
  "notification_channels": "wecom,telegram",
  "auth_enabled": false
}
```

---

## 分析管理

### POST /analysis/run

触发批量股票分析（异步执行）。

**请求体:**
```json
{
  "stocks": "600519,002594,hk00700",
  "dry_run": false
}
```

**响应:**
```json
{
  "status": "accepted",
  "message": "分析任务已提交",
  "stocks": "600519,002594,hk00700"
}
```

### POST /analysis/single

分析单只股票（同步，等待结果返回）。

**请求体:**
```json
{
  "stock_code": "600519"
}
```

### GET /analysis/history

获取分析历史列表。

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | string | 否 | 按股票代码过滤 |
| limit | int | 否 | 返回数量（默认 20） |

### GET /analysis/{id}

获取报告详情。

### DELETE /analysis/{id}

删除报告。

---

## 股票行情

### GET /stocks/{code}/quote

获取股票实时行情。

**示例:** `GET /api/v1/stocks/600519/quote`

**响应示例:**
```json
{
  "current_price": 1800.50,
  "change_pct": 2.35,
  "open_price": 1780.00,
  "high_price": 1810.00,
  "low_price": 1775.00,
  "volume": 12345678
}
```

### GET /stocks/{code}/history

获取股票历史数据。

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| days | int | 否 | 历史天数（默认 60） |

### GET /stocks/{code}/info

获取股票基本信息。

### GET /stocks/market/overview

大盘行情概览。

### GET /stocks/market/review

大盘复盘分析。

---

## 投资组合

### GET /portfolio

获取所有持仓。

### GET /portfolio/summary

获取组合汇总（总市值、总盈亏等）。

### GET /portfolio/risk

获取组合风险评估。

### POST /portfolio

新增持仓。

**请求体:**
```json
{
  "stockCode": "600519",
  "stockName": "贵州茅台",
  "quantity": 100,
  "costPrice": 1750.00
}
```

### POST /portfolio/refresh

刷新持仓实时市值。

### DELETE /portfolio/{id}

删除持仓。

---

## 告警管理

### GET /alerts

获取告警规则列表。

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | string | 否 | 按股票代码过滤 |

### POST /alerts

创建告警规则。

**请求体:**
```json
{
  "stockCode": "600519",
  "condition": "price_above",
  "threshold": 1900.00,
  "notifyChannels": "wecom,telegram"
}
```

### PUT /alerts/{id}

更新告警规则。

### DELETE /alerts/{id}

删除告警规则。

---

## 策略回测

### POST /backtest/run

执行策略回测。

**请求体:**
```json
{
  "stock_code": "600519",
  "strategy": "ma_golden_cross",
  "days": 180,
  "initial_capital": 100000
}
```

**支持的策略:**
| 策略代码 | 名称 | 说明 |
|---------|------|------|
| `ma_golden_cross` | 均线金叉 | MA5 上穿 MA20 买入，下穿卖出 |
| `volume_breakout` | 放量突破 | 成交量突破 2 倍均量且涨幅>3% 买入 |
| `bull_trend` | 牛趋势 | 价格在 MA10 和 MA30 之上时持有 |
| `shrink_pullback` | 缩量回调 | 趋势回调缩量后反弹买入 |
| `box_oscillation` | 箱体震荡 | 箱体下沿买入上沿卖出 |

### GET /backtest/history

获取回测历史。

### GET /backtest/strategies

获取所有可用策略列表。

---

## 决策信号

### GET /decision-signals

获取决策信号列表。

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | string | 否 | 按股票代码过滤 |
| status | string | 否 | 信号状态（active） |

### POST /decision-signals

创建决策信号。

### POST /decision-signals/{id}/execute

执行（确认）信号。

### POST /decision-signals/{id}/cancel

取消信号。

---

## AI 问股

### POST /agent/chat

与 AI Agent 对话。

**请求体:**
```json
{
  "message": "分析一下贵州茅台最近的走势",
  "session_id": "optional-session-id"
}
```

**响应:**
```json
{
  "reply": "...",
  "session_id": "xxx",
  "model": "gpt-4o"
}
```

---

## 智能情报

### GET /intelligence/{stockCode}

获取股票智能情报。

**参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 股票名称 |

### POST /intelligence/batch

批量获取情报。

**请求体:**
```json
{
  "stock_codes": ["600519", "002594"]
}
```

---

## 系统配置

### GET /system-config

获取系统配置（脱敏）。

### GET /system-config/notification-channels

获取支持的通知渠道列表。

### GET /system-config/markets

获取支持的市场列表。

---

## 用量统计

### GET /usage

获取 LLM 用量和分析统计。

**响应示例:**
```json
{
  "total_analyses": 156,
  "llm_model": "gpt-4o",
  "agent_mode": "true",
  "estimated_tokens_today": 0,
  "estimated_cost_today": "$0.00"
}
```

---

## 认证

### POST /auth/login

用户登录获取 Token。

**请求体:**
```json
{
  "password": "your-password"
}
```

**响应:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expires_in": 86400
}
```
