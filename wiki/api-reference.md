# REST API 参考

## 认证

系统采用 **JWT Bearer Token** 认证（JJWT 0.12.5）。

```http
Authorization: Bearer <token>
```

获取 Token：

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your_password"
}
```

> 定时任务调用接口通过 `SchedulerAuthConfig` 内置 Token 认证，无需手动管理。

## 通用响应格式

成功响应直接返回业务数据（无统一包装层）：

```json
// 列表
[...]

// 单对象
{...}

// 操作确认
{ "task_id": "xxx", "status": "pending", "message": "任务已提交" }
```

错误响应（由 `GlobalExceptionHandler` 统一处理）：

```json
{
  "error": "错误描述",
  "code": 400
}
```

---

## 分析模块 `/api/v1`

### 触发股票分析（异步）

```http
POST /api/v1/analysis/run
```

**请求体：**

```json
{
  "stock_code": "600519",
  "dry_run": false
}
```

**响应：**

```json
{
  "task_id": "abc12345-...",
  "status": "pending",
  "stock_code": "600519",
  "message": "分析任务已提交"
}
```

---

### 查询分析任务状态

```http
GET /api/v1/analysis/tasks/{taskId}
```

**响应：**

```json
{
  "task_id": "abc12345-...",
  "status": "completed",
  "progress": 100,
  "result": {...}
}
```

---

### 获取分析历史

```http
GET /api/v1/history?stockCode=600519&limit=20
```

---

### 获取分析报告详情

```http
GET /api/v1/history/{id}
```

---

### 获取实时行情

```http
GET /api/v1/stocks/{code}/quote
```

**响应：**

```json
{
  "stock_code": "600519",
  "stock_name": "贵州茅台",
  "current_price": 1680.00,
  "change_pct": 1.25,
  "volume": 12345678
}
```

---

### 获取市场概况

```http
GET /api/v1/market/overview
```

---

## 回测模块 `/api/v1/backtest`

### 运行回测

```http
POST /api/v1/backtest/run
```

**请求体：**

```json
{
  "stock_code": "600519",
  "strategy_id": "ma_golden_cross",
  "start_date": "2022-01-01",
  "end_date": "2024-01-01",
  "initial_capital": 100000,
  "config": {
    "execution_mode": "NEXT_OPEN",
    "commission_rate": 0.0003,
    "t1_enabled": true
  }
}
```

**响应（BacktestRecord）：**

```json
{
  "id": 1,
  "strategy_name": "ma_golden_cross",
  "total_return_pct": 35.6,
  "annual_return_pct": 16.8,
  "max_drawdown_pct": 12.3,
  "sharpe_ratio": 1.45,
  "win_rate_pct": 58.3,
  "total_trades": 24,
  "alpha_pct": 8.2
}
```

---

### 获取回测历史

```http
GET /api/v1/backtest/history?stockCode=600519&limit=10
```

---

### 获取权益曲线（可视化数据）

```http
GET /api/v1/backtest/{id}/visualization
```

---

### 参数优化

```http
POST /api/v1/backtest/parameter-optimize
```

**请求体：**

```json
{
  "stock_code": "600519",
  "strategy_id": "ma_golden_cross",
  "param_ranges": {
    "fast_period": [3, 5, 10],
    "slow_period": [20, 30, 60]
  },
  "start_date": "2020-01-01",
  "end_date": "2024-01-01"
}
```

---

### 前向验证

```http
POST /api/v1/backtest/walk-forward
```

---

### 蒙特卡洛模拟

```http
POST /api/v1/backtest/monte-carlo
```

---

### 多策略组合回测

```http
POST /api/v1/backtest/portfolio
```

---

## 决策信号模块 `/api/v1/signal`

### 获取信号列表

```http
GET /api/v1/signal/list?status=active&limit=20
```

---

### 获取信号详情

```http
GET /api/v1/signal/{id}
```

---

### 提交信号反馈

```http
POST /api/v1/signal/{id}/feedback
```

**请求体：**

```json
{
  "feedback_value": "useful",
  "reason_code": "good_call",
  "note": "入场时机准确，达到目标价"
}
```

---

## 智能选股模块 `/api/v1/screening`

### 提交选股任务

```http
POST /api/v1/screening/run
```

**请求体：**

```json
{
  "market": "cn",
  "strategy": "momentum",
  "max_results": 10
}
```

---

### 查询选股任务状态

```http
GET /api/v1/screening/tasks/{taskId}
```

---

## 投资组合模块 `/api/v1/portfolio`

### 获取所有账户

```http
GET /api/v1/portfolio/accounts
```

---

### 获取账户详情

```http
GET /api/v1/portfolio/accounts/{accountId}
```

---

## 模拟交易模块 `/api/v1/paper-trading`

### 创建模拟账户

```http
POST /api/v1/paper-trading/accounts
```

**请求体：**

```json
{
  "name": "我的模拟账户",
  "market": "cn",
  "initial_capital": 1000000
}
```

---

### 模拟买入

```http
POST /api/v1/paper-trading/accounts/{accountId}/buy
```

**请求体：**

```json
{
  "stock_code": "600519",
  "quantity": 100,
  "price": 1680.00
}
```

---

### 模拟卖出

```http
POST /api/v1/paper-trading/accounts/{accountId}/sell
```

---

### 获取持仓

```http
GET /api/v1/paper-trading/accounts/{accountId}/positions
```

---

## 告警模块 `/api/v1/alert`

### 创建告警规则

```http
POST /api/v1/alert/rules
```

**请求体：**

```json
{
  "name": "茅台价格告警",
  "stock_code": "600519",
  "alert_type": "price_above",
  "threshold_value": 1700,
  "severity": "medium",
  "notify_channels": "email,webhook"
}
```

---

### 获取告警规则列表

```http
GET /api/v1/alert/rules
```

---

### 更新告警规则

```http
PUT /api/v1/alert/rules/{id}
```

---

### 删除告警规则

```http
DELETE /api/v1/alert/rules/{id}
```

---

### 获取告警触发历史

```http
GET /api/v1/alert/triggers?limit=20
```

---

## AI 对话模块 `/api/v1/chat`

### 创建会话

```http
POST /api/v1/chat/sessions
```

---

### 发送消息

```http
POST /api/v1/chat/sessions/{sessionId}/messages
```

**请求体：**

```json
{
  "content": "帮我分析一下贵州茅台的技术面",
  "skill_name": "stock_analysis"
}
```

---

### 获取会话历史

```http
GET /api/v1/chat/sessions/{sessionId}/messages
```

---

## 自选股模块 `/api/v1/watchlist`

### 获取自选股

```http
GET /api/v1/watchlist
```

### 添加自选股

```http
POST /api/v1/watchlist
```

**请求体：**

```json
{
  "stock_code": "600519",
  "stock_name": "贵州茅台"
}
```

### 删除自选股

```http
DELETE /api/v1/watchlist/{stockCode}
```

---

## 策略模块 `/api/v1/strategy`

### 获取策略目录

```http
GET /api/v1/strategy/catalog
```

**响应示例：**

```json
{
  "strategies": [
    {
      "id": "ma_golden_cross",
      "name": "均线金叉策略",
      "category": "technical",
      "capabilities": ["backtest"],
      "runtime": "implemented"
    }
  ],
  "categories": {
    "technical": "技术面",
    "fundamental": "基本面"
  }
}
```

---

## 系统模块 `/api/v1/system`

### 健康检查

```http
GET /api/v1/system/health
```

### 用量统计

```http
GET /api/v1/system/usage
```

### 仪表盘数据

```http
GET /api/v1/system/dashboard
```

---

## Actuator 监控端点

Spring Boot Actuator 暴露以下端点（需授权）：

```
GET /actuator/health    # 应用健康状态
GET /actuator/info      # 应用信息
GET /actuator/metrics   # Micrometer 指标
GET /actuator/prometheus # Prometheus 指标（需集成）
```
