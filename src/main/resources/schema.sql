-- ============================================================
-- 股票智能分析系统 - SQLite 表结构初始化
-- 对齐 dsa-web React 版本全部功能
-- ============================================================

-- ========== 告警系统 ==========
-- 支持用户自定义价格/技术指标/事件告警，触发后通过多渠道推送通知

-- 告警规则表：定义告警条件与推送策略
CREATE TABLE IF NOT EXISTS alert_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100),                          -- 规则名称（用户可自定义）
    stock_code VARCHAR(20),                     -- 绑定的股票代码（target_scope=specific 时使用）
    stock_name VARCHAR(50),                     -- 股票名称（冗余存储，方便展示）
    alert_type VARCHAR(30) NOT NULL,            -- 告警类型: price_above/price_below/volume_spike/ma_cross 等
    target_scope VARCHAR(20) DEFAULT 'specific',-- 监控范围: specific(单只) / watchlist(自选股) / market(全市场)
    target VARCHAR(200),                        -- 监控目标描述（JSON 或逗号分隔的代码列表）
    severity VARCHAR(20) DEFAULT 'medium',      -- 严重程度: low/medium/high/critical
    threshold_value REAL,                       -- 阈值（如价格、涨跌幅百分比）
    condition_expr TEXT,                        -- 复杂条件表达式（JSON 格式，支持组合条件）
    parameters TEXT,                            -- 附加参数（JSON 格式，如均线周期等）
    enabled INTEGER DEFAULT 1,                  -- 是否启用: 1=启用, 0=禁用
    triggered INTEGER DEFAULT 0,                -- 是否已触发: 1=已触发
    last_triggered_at TIMESTAMP,                -- 最近一次触发时间
    one_shot INTEGER DEFAULT 0,                 -- 是否一次性规则: 1=触发后自动禁用
    notify_channels VARCHAR(200),               -- 通知渠道（逗号分隔）: email/wechat/webhook/bark
    source VARCHAR(20) DEFAULT 'manual',        -- 规则来源: manual(手动创建) / system(系统生成)
    note VARCHAR(500),                          -- 备注说明
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 告警触发记录表：记录每次规则被触发的详情
CREATE TABLE IF NOT EXISTS alert_triggers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id INTEGER NOT NULL,                   -- 关联的告警规则 ID
    target VARCHAR(100),                        -- 触发目标（股票代码）
    display_target VARCHAR(100),                -- 展示用目标名称（如 "贵州茅台 600519"）
    status VARCHAR(20) DEFAULT 'triggered',     -- 触发状态: triggered/acknowledged/resolved
    observed_value REAL,                        -- 实际观测值（触发时的真实数据）
    threshold_value REAL,                       -- 触发时的阈值快照
    message TEXT,                               -- 告警消息内容（人可读的描述）
    triggered_at TIMESTAMP,                     -- 触发时间
    FOREIGN KEY (rule_id) REFERENCES alert_rules(id)
);

-- 告警通知发送记录表：跟踪每条通知的投递状态
CREATE TABLE IF NOT EXISTS alert_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trigger_id INTEGER NOT NULL,                 -- 关联的触发记录 ID
    channel VARCHAR(30) NOT NULL,               -- 发送渠道: email/wechat/webhook/bark
    success INTEGER DEFAULT 0,                  -- 是否发送成功: 1=成功, 0=失败
    error_code VARCHAR(50),                     -- 失败时的错误码
    error_message TEXT,                         -- 失败时的错误信息
    sent_at TIMESTAMP,                          -- 发送时间
    FOREIGN KEY (trigger_id) REFERENCES alert_triggers(id)
);

-- ========== 分析系统 ==========
-- AI 多维度分析报告，包含技术面、基本面、舆情分析结果

-- 分析报告表：存储每次 AI 分析的完整结果
CREATE TABLE IF NOT EXISTS analysis_report (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,            -- 股票代码
    stock_name VARCHAR(50),                     -- 股票名称
    analysis_date TIMESTAMP,                    -- 分析执行时间
    market VARCHAR(10),                         -- 市场: cn/us/hk
    current_price REAL,                         -- 分析时的当前价格
    change_pct REAL,                            -- 当日涨跌幅(%)
    total_score INTEGER,                        -- 综合评分(0-100)
    signal VARCHAR(20),                         -- 交易信号: buy/sell/hold/strong_buy/strong_sell
    confidence REAL,                            -- 信号置信度(0.0-1.0)
    summary TEXT,                               -- 分析摘要（一段话概括）
    technical_analysis TEXT,                    -- 技术面分析详情（JSON/Markdown）
    fundamental_analysis TEXT,                  -- 基本面分析详情
    news_analysis TEXT,                         -- 舆情/新闻分析详情
    full_report TEXT,                           -- 完整报告（格式化后的最终输出）
    llm_response TEXT,                          -- LLM 原始响应（用于调试和审计）
    agent_mode VARCHAR(20),                     -- Agent 模式: single/multi/orchestrated
    llm_model VARCHAR(100),                     -- 使用的 LLM 模型名称
    duration_seconds REAL,                      -- 分析耗时（秒）
    token_usage INTEGER,                        -- Token 消耗量
    is_dry_run INTEGER DEFAULT 0,               -- 是否试运行: 1=仅测试不记入正式报告
    report_language VARCHAR(10) DEFAULT 'zh',   -- 报告语言: zh/en
    skills TEXT,                                -- 启用的分析技能列表（JSON 数组）
    analysis_phase VARCHAR(20),                 -- 分析阶段: screening/deep/tracking
    selection_source VARCHAR(50),               -- 选股来源: manual/watchlist/alphasift
    task_id VARCHAR(50),                        -- 关联的异步任务 ID
    created_at TIMESTAMP
);

-- ========== 回测系统 ==========
-- 策略历史回测 + 决策信号事后验证

-- 回测记录表：策略回测结果 & 信号事后评估
CREATE TABLE IF NOT EXISTS backtest_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,            -- 回测标的代码
    stock_name VARCHAR(50),                     -- 回测标的名称
    strategy_name VARCHAR(50),                  -- 策略名称（如 ma_golden_cross）
    start_date TIMESTAMP,                       -- 回测起始日期
    end_date TIMESTAMP,                         -- 回测结束日期
    initial_capital REAL,                       -- 初始资金
    final_capital REAL,                         -- 最终资金
    total_return_pct REAL,                      -- 总收益率(%)
    annual_return_pct REAL,                     -- 年化收益率(%)
    max_drawdown_pct REAL,                      -- 最大回撤(%)
    sharpe_ratio REAL,                          -- 夏普比率
    win_rate_pct REAL,                          -- 胜率(%)
    total_trades INTEGER,                       -- 总交易次数
    winning_trades INTEGER,                     -- 盈利交易次数
    losing_trades INTEGER,                      -- 亏损交易次数
    avg_holding_days REAL,                      -- 平均持仓天数
    profit_loss_ratio REAL,                     -- 盈亏比
    benchmark_return_pct REAL,                  -- 基准收益率(%)（如沪深300）
    alpha_pct REAL,                             -- 超额收益 Alpha(%)
    trade_details TEXT,                         -- 交易明细（JSON 数组）
    parameters TEXT,                            -- 策略参数（JSON）
    -- ---- 信号回溯评估字段 ----
    signal_id INTEGER,                          -- 关联的决策信号 ID
    market_phase VARCHAR(30),                   -- 评估时的市场阶段: bull/bear/consolidation
    market_phase_summary TEXT,                  -- 市场阶段描述
    action VARCHAR(20),                         -- 原始信号动作: buy/sell/hold
    direction_expected VARCHAR(10),             -- 预期方向: up/down
    actual_movement VARCHAR(10),               -- 实际走势: up/down/flat
    outcome VARCHAR(20),                        -- 评估结果: correct/incorrect/partial
    eval_status VARCHAR(20) DEFAULT 'completed',-- 评估状态: pending/completed/error
    eval_window_days INTEGER,                   -- 评估观察窗口（天数）
    return_pct REAL,                            -- 信号发出后实际收益率(%)
    analysis_date TIMESTAMP,                    -- 信号原始分析日期
    diagnostics TEXT,                           -- 诊断信息（JSON，记录评估细节）
    created_at TIMESTAMP
);

-- ========== 决策信号系统 ==========
-- AI Agent 生成的结构化交易决策信号，带有完整的入场/止损/目标价计划

-- 决策信号表：AI 生成的交易建议与执行计划
CREATE TABLE IF NOT EXISTS decision_signals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,            -- 股票代码
    stock_name VARCHAR(50),                     -- 股票名称
    market VARCHAR(10),                         -- 市场: cn/us/hk
    source_type VARCHAR(30),                    -- 信号来源类型: agent_analysis/manual/screening
    source_agent VARCHAR(50),                   -- 生成信号的 Agent 名称
    source_report_id INTEGER,                   -- 关联的分析报告 ID
    trace_id VARCHAR(50),                       -- 链路追踪 ID（跨表关联用）
    market_phase VARCHAR(30),                   -- 当前市场阶段判断
    trigger_source VARCHAR(50),                 -- 触发来源: scheduled/manual/alert
    action VARCHAR(20),                         -- 建议动作: strong_buy/buy/hold/sell/strong_sell
    action_label VARCHAR(50),                   -- 动作标签（人可读描述）
    confidence REAL,                            -- 信号置信度(0.0-1.0)
    score INTEGER,                              -- 综合评分(0-100)
    horizon VARCHAR(20),                        -- 投资期限: short/medium/long
    entry_low REAL,                             -- 建议入场价下限
    entry_high REAL,                            -- 建议入场价上限
    stop_loss REAL,                             -- 止损价位
    target_price REAL,                          -- 目标价位
    invalidation TEXT,                          -- 信号失效条件描述
    watch_conditions TEXT,                      -- 观察条件（需满足后才执行）
    reason TEXT,                                -- 推荐理由
    risk_summary TEXT,                          -- 风险摘要
    catalyst_summary TEXT,                      -- 催化剂/驱动因素摘要
    evidence TEXT,                              -- 支撑证据（JSON 数组）
    data_quality_summary TEXT,                  -- 数据质量评估
    plan_quality VARCHAR(20),                   -- 计划质量: high/medium/low
    status VARCHAR(20) DEFAULT 'active',        -- 信号状态: active/expired/executed/cancelled
    expires_at TIMESTAMP,                       -- 信号过期时间
    metadata TEXT,                              -- 扩展元数据（JSON）
    report_language VARCHAR(10) DEFAULT 'zh',   -- 报告语言
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 信号结果评估表：追踪信号发出后的实际市场表现
CREATE TABLE IF NOT EXISTS decision_signal_outcomes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    signal_id INTEGER NOT NULL,                 -- 关联的决策信号 ID
    horizon VARCHAR(20),                        -- 评估的时间窗口: short/medium/long
    engine_version VARCHAR(20),                 -- 评估引擎版本号
    eval_status VARCHAR(20),                    -- 评估状态: completed/pending/unable
    outcome VARCHAR(20),                        -- 结果: correct/incorrect/partial/expired
    return_pct REAL,                            -- 区间实际收益率(%)
    entry_price REAL,                           -- 信号发出时的价格
    exit_price REAL,                            -- 评估窗口结束时的价格
    actual_movement VARCHAR(10),               -- 实际走势方向: up/down/flat
    direction_expected VARCHAR(10),             -- 信号预期方向: up/down
    unable_reason VARCHAR(100),                 -- 无法评估的原因（如停牌、数据缺失）
    evaluated_at TIMESTAMP,                     -- 评估执行时间
    created_at TIMESTAMP,
    FOREIGN KEY (signal_id) REFERENCES decision_signals(id)
);

-- 信号用户反馈表：用户对信号质量的主观评价
CREATE TABLE IF NOT EXISTS decision_signal_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    signal_id INTEGER NOT NULL UNIQUE,          -- 关联的决策信号 ID（每个信号仅一条反馈）
    feedback_value VARCHAR(20),                 -- 反馈评价: useful/not_useful/neutral
    reason_code VARCHAR(50),                    -- 反馈原因码: timing_off/wrong_direction/good_call 等
    note TEXT,                                  -- 用户自由文本反馈
    source VARCHAR(30),                         -- 反馈来源: web/bot/api
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (signal_id) REFERENCES decision_signals(id)
);

-- ========== 投资组合系统 ==========
-- 支持多账户、多市场的持仓管理，包含交易记录、资金流水、公司行动

-- 投资账户表：管理模拟交易账户的现金余额与融资
CREATE TABLE IF NOT EXISTS portfolio_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,                 -- 账户名称（用户自定义）
    broker VARCHAR(50),                         -- 券商名称（记录用，不对接）
    market VARCHAR(10),                         -- 主要市场: cn/us/hk
    base_currency VARCHAR(10) DEFAULT 'CNY',    -- 基础货币: CNY/USD/HKD
    cash_balance REAL DEFAULT 0,                -- 当前现金余额
    loan_balance REAL DEFAULT 0,                -- 当前融资负债
    loan_limit REAL DEFAULT 0,                  -- 融资额度上限
    owner_id VARCHAR(50),                       -- 所属用户 ID（预留多用户扩展）
    is_active INTEGER DEFAULT 1,                -- 是否活跃: 1=活跃, 0=已归档
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 持仓明细表：当前账户的股票持仓状态
CREATE TABLE IF NOT EXISTS portfolio_positions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,                         -- 所属账户 ID
    stock_code VARCHAR(20) NOT NULL,            -- 股票代码
    stock_name VARCHAR(50),                     -- 股票名称
    market VARCHAR(10),                         -- 市场
    quantity INTEGER,                           -- 持仓数量（股）
    cost_price REAL,                            -- 成本价（含手续费均摊）
    current_price REAL,                         -- 当前价格（最近更新）
    profit_loss REAL,                           -- 浮动盈亏（金额）
    profit_loss_pct REAL,                       -- 浮动盈亏率(%)
    market_value REAL,                          -- 当前市值
    position_pct REAL,                          -- 仓位占比(%)
    buy_date TIMESTAMP,                         -- 首次买入日期
    stop_loss_price REAL,                       -- 止损价位
    target_price REAL,                          -- 目标价位
    tags VARCHAR(200),                          -- 标签（逗号分隔）: 价值/成长/短线 等
    note TEXT,                                  -- 持仓备注
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

-- 交易记录表：记录每笔买入/卖出操作
CREATE TABLE IF NOT EXISTS portfolio_trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,                -- 所属账户 ID
    symbol VARCHAR(20) NOT NULL,                -- 交易标的代码
    trade_date DATE NOT NULL,                   -- 交易日期
    side VARCHAR(10) NOT NULL,                  -- 交易方向: buy/sell
    quantity REAL NOT NULL,                     -- 交易数量
    price REAL NOT NULL,                        -- 成交价格
    fee REAL DEFAULT 0,                         -- 手续费（佣金）
    tax REAL DEFAULT 0,                         -- 印花税/其他税费
    market VARCHAR(10),                         -- 市场
    currency VARCHAR(10) DEFAULT 'CNY',         -- 结算货币
    trade_uid VARCHAR(50),                      -- 交易唯一标识（用于去重导入）
    note TEXT,                                  -- 交易备注
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

-- 资金流水表：记录账户的出入金操作
CREATE TABLE IF NOT EXISTS cash_ledger (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,                -- 所属账户 ID
    event_date DATE NOT NULL,                   -- 发生日期
    direction VARCHAR(10) NOT NULL,             -- 资金方向: deposit(入金) / withdraw(出金)
    amount REAL NOT NULL,                       -- 金额
    currency VARCHAR(10) DEFAULT 'CNY',         -- 货币
    note TEXT,                                  -- 备注
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

-- 公司行动表：分红、拆股等影响持仓的公司事件
CREATE TABLE IF NOT EXISTS corporate_actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,                -- 所属账户 ID
    symbol VARCHAR(20) NOT NULL,                -- 标的代码
    effective_date DATE NOT NULL,               -- 生效日期（除权除息日）
    action_type VARCHAR(30) NOT NULL,           -- 事件类型: cash_dividend/stock_split/rights_issue
    market VARCHAR(10),                         -- 市场
    currency VARCHAR(10) DEFAULT 'CNY',         -- 货币
    cash_dividend_per_share REAL,               -- 每股现金分红
    split_ratio REAL,                           -- 拆股比例（如 10 表示 1 拆 10）
    note TEXT,                                  -- 备注
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

-- ========== 行情数据 ==========
-- 缓存的股票日线行情数据，用于技术分析和回测

-- 日线行情表：每日 OHLCV 数据
CREATE TABLE IF NOT EXISTS stock_daily_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,            -- 股票代码
    stock_name VARCHAR(50),                     -- 股票名称
    trade_date DATE NOT NULL,                   -- 交易日期
    open_price REAL,                            -- 开盘价
    high_price REAL,                            -- 最高价
    low_price REAL,                             -- 最低价
    close_price REAL,                           -- 收盘价
    volume INTEGER,                             -- 成交量（股）
    amount REAL,                                -- 成交额（元）
    change_pct REAL,                            -- 涨跌幅(%)
    change_amount REAL,                         -- 涨跌额（元）
    turnover_rate REAL,                         -- 换手率(%)
    amplitude REAL,                             -- 振幅(%)
    data_source VARCHAR(30),                    -- 数据来源: eastmoney/tushare/yahoo
    created_at TIMESTAMP
);

-- ========== 自选股 ==========
-- 用户关注的股票列表，支持批量分析和告警监控

-- 自选股表：用户的关注列表
CREATE TABLE IF NOT EXISTS watchlist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL UNIQUE,     -- 股票代码（唯一约束，不可重复添加）
    stock_name VARCHAR(50),                     -- 股票名称
    market VARCHAR(10),                         -- 市场: cn/us/hk
    added_at TIMESTAMP                          -- 加入时间
);

-- ========== AI对话系统 ==========
-- 支持多轮对话的 AI 聊天功能，按会话管理消息历史

-- 对话会话表：管理独立的聊天会话
CREATE TABLE IF NOT EXISTS chat_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(50) NOT NULL UNIQUE,     -- 会话唯一标识（UUID）
    title VARCHAR(200),                         -- 会话标题（自动生成或用户自定义）
    message_count INTEGER DEFAULT 0,            -- 消息总数（冗余计数，加速查询）
    created_at TIMESTAMP,                       -- 会话创建时间
    last_active TIMESTAMP                       -- 最后活跃时间（用于排序）
);

-- 对话消息表：存储每条聊天消息
CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(50) NOT NULL,            -- 所属会话 ID
    message_id VARCHAR(50) NOT NULL,            -- 消息唯一标识
    role VARCHAR(20) NOT NULL,                  -- 消息角色: user/assistant/system
    content TEXT NOT NULL,                      -- 消息内容
    skill_name VARCHAR(50),                     -- 使用的技能名称（如 stock_analysis/backtest）
    created_at TIMESTAMP,                       -- 消息时间
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
);

-- ========== LLM用量统计 ==========
-- 按天按模型统计 LLM API 调用量与费用，用于成本监控

-- LLM 日用量表：每日调用量与 Token 消耗统计
CREATE TABLE IF NOT EXISTS llm_usage_daily (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usage_date DATE NOT NULL,                   -- 统计日期
    model VARCHAR(100),                         -- 模型名称（如 gpt-4/qwen-max）
    provider VARCHAR(50),                       -- 供应商: openai/dashscope/ollama
    request_count INTEGER DEFAULT 0,            -- 当日请求次数
    prompt_tokens INTEGER DEFAULT 0,            -- 输入 Token 总量
    completion_tokens INTEGER DEFAULT 0,        -- 输出 Token 总量
    total_tokens INTEGER DEFAULT 0,             -- 总 Token 消耗
    total_cost REAL DEFAULT 0,                  -- 估算费用（元）
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ========== 智能选股 ==========
-- AI 驱动的全市场扫描选股任务（AlphaSift）

-- 选股任务表：异步执行的 AI 选股任务
CREATE TABLE IF NOT EXISTS alphasift_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id VARCHAR(50) NOT NULL UNIQUE,        -- 任务唯一标识（UUID）
    market VARCHAR(10) DEFAULT 'cn',            -- 扫描市场: cn/us/hk
    strategy VARCHAR(50),                       -- 选股策略名称
    max_results INTEGER DEFAULT 10,             -- 最大返回结果数
    status VARCHAR(20) DEFAULT 'pending',       -- 任务状态: pending/running/completed/failed
    progress INTEGER DEFAULT 0,                 -- 进度百分比(0-100)
    result TEXT,                                -- 选股结果（JSON 数组）
    error_message TEXT,                         -- 失败时的错误信息
    created_at TIMESTAMP,                       -- 任务创建时间
    completed_at TIMESTAMP                      -- 任务完成时间
);

-- ========== 异步分析任务 ==========
-- 耗时较长的分析操作（单股/批量）通过异步任务队列执行

-- 异步分析任务表：管理后台执行的分析任务生命周期
CREATE TABLE IF NOT EXISTS analysis_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id VARCHAR(50) NOT NULL UNIQUE,        -- 任务唯一标识（UUID）
    stock_code VARCHAR(20),                     -- 单股分析时的股票代码
    stock_codes TEXT,                           -- 批量分析时的代码列表（逗号分隔）
    task_type VARCHAR(20) DEFAULT 'analysis',   -- 任务类型: analysis/batch_analysis/screening
    status VARCHAR(20) DEFAULT 'pending',       -- 任务状态: pending/running/completed/failed/cancelled
    progress INTEGER DEFAULT 0,                 -- 进度百分比(0-100)
    message TEXT,                               -- 当前状态描述（如 "正在分析第3/5只..."）
    result TEXT,                                -- 任务结果摘要（JSON）
    error_message TEXT,                         -- 失败时的错误信息
    skills TEXT,                                -- 启用的分析技能（JSON 数组）
    analysis_phase VARCHAR(20),                 -- 分析阶段: screening/deep/tracking
    report_language VARCHAR(10) DEFAULT 'zh',   -- 报告语言: zh/en
    created_at TIMESTAMP,                       -- 任务创建时间
    started_at TIMESTAMP,                       -- 任务开始执行时间
    completed_at TIMESTAMP                      -- 任务完成时间
);

-- ========== 因子自进化系统 ==========
-- LLM 驱动的因子自动发现、评估、进化与记忆存储
-- 对应论文 FactorMiner + AlphaAgentEvo 的因子自进化架构

-- 因子候选表：LLM 生成的每个因子候选（含遗传信息）
CREATE TABLE IF NOT EXISTS factor_candidates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    factor_id VARCHAR(50) NOT NULL UNIQUE,         -- 因子 UUID
    factor_name VARCHAR(100) NOT NULL,              -- 因子名称（如 vol_weighted_momentum_20d）
    factor_expression TEXT NOT NULL,                -- 因子计算表达式（DSL 代码）
    factor_type VARCHAR(30) DEFAULT 'SIMPLE',      -- 因子类型: SIMPLE/COMPOSITE/CROSS_SECTIONAL/TIME_SERIES/EVENT_DRIVEN
    category VARCHAR(50) DEFAULT 'custom',          -- 因子分类: momentum/mean_reversion/volatility/volume/trend/custom
    description TEXT,                                -- LLM 生成的因子说明
    generation_round INTEGER DEFAULT 0,             -- 进化代数（第几轮生成）
    parent_factor_id VARCHAR(50),                  -- 父代因子 ID（变异来源）
    second_parent_factor_id VARCHAR(50),           -- 第二父代 ID（仅 CROSSBREED）
    mutation_type VARCHAR(30) DEFAULT 'INITIAL',   -- 变异类型: INITIAL/PARAM_MUTATE/EXPR_MUTATE/CROSSBREED/INVERSE_MUTATE/CONDITION_SPECIALIZE/COMBINE
    parameters TEXT,                                -- 因子参数（JSON）
    market_condition VARCHAR(50) DEFAULT 'any',    -- 适用市场条件
    status VARCHAR(20) DEFAULT 'CANDIDATE',         -- 生命周期: CANDIDATE/EVALUATING/VALIDATED/PROMOTED/DEPRECATED/MUTATED
    generation_reasoning TEXT,                      -- LLM 生成推理过程
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 因子评估结果表：每个因子候选的量化评估指标
CREATE TABLE IF NOT EXISTS factor_evaluations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    factor_id VARCHAR(50) NOT NULL,                 -- 关联的因子 ID
    ic REAL,                                        -- 信息系数（IC）
    ic_mean REAL,                                   -- IC 均值
    ic_std REAL,                                    -- IC 标准差
    ir REAL,                                        -- 信息比率（IR）
    sharpe_ratio REAL,                              -- 夏普比率
    max_drawdown_pct REAL,                          -- 最大回撤(%)
    win_rate_pct REAL,                              -- 胜率(%)
    total_return_pct REAL,                          -- 总收益率(%)
    coverage_rate REAL,                             -- 覆盖率(0-1)
    turnover_rate REAL,                             -- 换手率(0-1)
    ic_decay_days INTEGER,                          -- IC 衰减天数
    overall_score REAL,                             -- 综合评分(0-100)
    is_passing INTEGER DEFAULT 0,                   -- 是否通过门槛: 1=通过, 0=未通过
    backtest_result TEXT,                           -- 完整回测结果（JSON）
    diagnostics TEXT,                               -- 诊断信息（JSON）
    evaluation_date DATE,                           -- 评估日期
    created_at TIMESTAMP,
    FOREIGN KEY (factor_id) REFERENCES factor_candidates(factor_id)
);

-- 因子进化记忆表：跨代进化经验的持久化记录
CREATE TABLE IF NOT EXISTS factor_evolution_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id VARCHAR(50) NOT NULL UNIQUE,          -- 记录 UUID
    factor_id VARCHAR(50) NOT NULL,                 -- 关联的因子 ID
    factor_name VARCHAR(100),                       -- 因子名称快照
    factor_expression TEXT,                         -- 因子表达式快照
    generation_round INTEGER,                       -- 进化代数
    mutation_type VARCHAR(30),                      -- 变异类型
    parent_factor_id VARCHAR(50),                  -- 父代因子 ID
    second_parent_factor_id VARCHAR(50),            -- 第二父代 ID
    factor_type VARCHAR(30),                        -- 因子类型
    category VARCHAR(50),                            -- 因子分类
    market_condition VARCHAR(50),                   -- 适用市场条件
    evaluation_score REAL,                           -- 评估得分快照
    ic REAL,                                        -- IC 快照
    ir REAL,                                        -- IR 快照
    sharpe_ratio REAL,                              -- 夏普比率快照
    status VARCHAR(20),                             -- 因子状态
    failure_reason TEXT,                            -- 失败原因（淘汰时填写）
    failure_patterns TEXT,                           -- 失败模式列表（JSON 数组）
    generation_reasoning TEXT,                      -- LLM 生成推理
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 因子失败模式表：从历史淘汰因子中提取的共性特征
CREATE TABLE IF NOT EXISTS factor_failure_patterns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern_signature VARCHAR(200) NOT NULL,       -- 失败模式签名
    occurrence_count INTEGER DEFAULT 1,             -- 出现次数
    avg_ic REAL,                                    -- 平均 IC
    avg_score REAL,                                 -- 平均评估得分
    failure_description TEXT,                      -- 典型失败原因描述
    factor_category VARCHAR(50),                   -- 涉及的因子分类
    first_seen_at TIMESTAMP,                        -- 首次发现时间
    last_seen_at TIMESTAMP,                         -- 最近发现时间
    UNIQUE(pattern_signature)
);

-- 进化因子注册表：已提升到生产因子库的进化因子
CREATE TABLE IF NOT EXISTS evolved_factor_registry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    factor_id VARCHAR(50) NOT NULL UNIQUE,         -- 因子 ID
    factor_name VARCHAR(100) NOT NULL UNIQUE,      -- 因子名称（生产库内唯一）
    factor_expression TEXT NOT NULL,               -- 因子表达式
    category VARCHAR(50),                           -- 因子分类
    description TEXT,                               -- 因子描述
    ic REAL,                                        -- 注册时的 IC
    ir REAL,                                        -- 注册时的 IR
    sharpe_ratio REAL,                              -- 注册时的夏普比率
    overall_score REAL,                              -- 注册时的综合评分
    generation_round INTEGER,                       -- 来源进化代数
    market_condition VARCHAR(50),                   -- 适用市场条件
    is_active INTEGER DEFAULT 1,                    -- 是否激活: 1=激活, 0=已淘汰
    deactivated_reason VARCHAR(200),               -- 淘汰原因
    signal_correct_count INTEGER DEFAULT 0,         -- 信号正确次数（实盘反馈）
    signal_incorrect_count INTEGER DEFAULT 0,      -- 信号错误次数（实盘反馈）
    registered_at TIMESTAMP,                        -- 注册时间
    deactivated_at TIMESTAMP,                       -- 淘汰时间
    FOREIGN KEY (factor_id) REFERENCES factor_candidates(factor_id)
);

-- ========== 自定义策略系统 ==========
-- 用户通过 API 创建/编辑的策略定义，与内置 YAML 策略共存
-- 生命周期：DRAFT → TESTING → PUBLISHED → DEPRECATED

-- 自定义策略表：存储用户创建的策略 YAML 内容及生命周期
CREATE TABLE IF NOT EXISTS custom_strategies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    strategy_id VARCHAR(100) NOT NULL UNIQUE,      -- 策略唯一标识（如 my_ma_cross）
    label VARCHAR(100),                             -- 中文展示名
    description TEXT,                               -- 策略描述
    category VARCHAR(30) NOT NULL,                  -- 分类: technical/fundamental/sentiment/event
    yaml_content TEXT NOT NULL,                     -- 完整策略 YAML 内容
    lifecycle_state VARCHAR(20) DEFAULT 'DRAFT',    -- 生命周期: DRAFT/TESTING/PUBLISHED/DEPRECATED
    version INTEGER DEFAULT 1,                      -- 版本号，每次更新递增
    capabilities TEXT,                              -- 能力列表（JSON 数组: backtest/scoring/screening）
    source_strategy_id VARCHAR(100),               -- 克隆来源策略 ID（NULL 表示原创）
    validation_status VARCHAR(20) DEFAULT 'pending',-- 校验状态: pending/valid/invalid
    validation_errors TEXT,                          -- 校验错误信息（JSON 数组）
    last_validated_at TIMESTAMP,                     -- 最近校验时间
    created_by VARCHAR(50) DEFAULT 'api',            -- 创建来源: api/bot/clone
    note TEXT,                                      -- 备注
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 策略版本历史表：记录每次策略修改的历史快照，支持回滚
CREATE TABLE IF NOT EXISTS custom_strategy_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    strategy_id VARCHAR(100) NOT NULL,              -- 关联的策略 ID
    version INTEGER NOT NULL,                       -- 版本号
    yaml_content TEXT NOT NULL,                     -- 该版本的 YAML 快照
    label VARCHAR(100),                             -- 该版本的标签
    description TEXT,                               -- 该版本的描述
    change_note TEXT,                               -- 变更说明
    created_at TIMESTAMP,
    UNIQUE(strategy_id, version),
    FOREIGN KEY (strategy_id) REFERENCES custom_strategies(strategy_id)
);
