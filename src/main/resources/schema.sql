-- 股票智能分析系统 - SQLite 表结构初始化
-- 对齐 dsa-web React 版本全部功能

-- ========== 告警系统 ==========

CREATE TABLE IF NOT EXISTS alert_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100),
    stock_code VARCHAR(20),
    stock_name VARCHAR(50),
    alert_type VARCHAR(30) NOT NULL,
    target_scope VARCHAR(20) DEFAULT 'specific',
    target VARCHAR(200),
    severity VARCHAR(20) DEFAULT 'medium',
    threshold_value REAL,
    condition_expr TEXT,
    parameters TEXT,
    enabled INTEGER DEFAULT 1,
    triggered INTEGER DEFAULT 0,
    last_triggered_at TIMESTAMP,
    one_shot INTEGER DEFAULT 0,
    notify_channels VARCHAR(200),
    source VARCHAR(20) DEFAULT 'manual',
    note VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS alert_triggers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id INTEGER NOT NULL,
    target VARCHAR(100),
    display_target VARCHAR(100),
    status VARCHAR(20) DEFAULT 'triggered',
    observed_value REAL,
    threshold_value REAL,
    message TEXT,
    triggered_at TIMESTAMP,
    FOREIGN KEY (rule_id) REFERENCES alert_rules(id)
);

CREATE TABLE IF NOT EXISTS alert_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trigger_id INTEGER NOT NULL,
    channel VARCHAR(30) NOT NULL,
    success INTEGER DEFAULT 0,
    error_code VARCHAR(50),
    error_message TEXT,
    sent_at TIMESTAMP,
    FOREIGN KEY (trigger_id) REFERENCES alert_triggers(id)
);

-- ========== 分析系统 ==========

CREATE TABLE IF NOT EXISTS analysis_report (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    analysis_date TIMESTAMP,
    market VARCHAR(10),
    current_price REAL,
    change_pct REAL,
    total_score INTEGER,
    signal VARCHAR(20),
    confidence REAL,
    summary TEXT,
    technical_analysis TEXT,
    fundamental_analysis TEXT,
    news_analysis TEXT,
    full_report TEXT,
    llm_response TEXT,
    agent_mode VARCHAR(20),
    llm_model VARCHAR(100),
    duration_seconds REAL,
    token_usage INTEGER,
    is_dry_run INTEGER DEFAULT 0,
    report_language VARCHAR(10) DEFAULT 'zh',
    skills TEXT,
    analysis_phase VARCHAR(20),
    selection_source VARCHAR(50),
    task_id VARCHAR(50),
    created_at TIMESTAMP
);

-- ========== 回测系统 ==========

CREATE TABLE IF NOT EXISTS backtest_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    strategy_name VARCHAR(50),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    initial_capital REAL,
    final_capital REAL,
    total_return_pct REAL,
    annual_return_pct REAL,
    max_drawdown_pct REAL,
    sharpe_ratio REAL,
    win_rate_pct REAL,
    total_trades INTEGER,
    winning_trades INTEGER,
    losing_trades INTEGER,
    avg_holding_days REAL,
    profit_loss_ratio REAL,
    benchmark_return_pct REAL,
    alpha_pct REAL,
    trade_details TEXT,
    parameters TEXT,
    -- 新增: 信号回溯字段
    signal_id INTEGER,
    market_phase VARCHAR(30),
    market_phase_summary TEXT,
    action VARCHAR(20),
    direction_expected VARCHAR(10),
    actual_movement VARCHAR(10),
    outcome VARCHAR(20),
    eval_status VARCHAR(20) DEFAULT 'completed',
    eval_window_days INTEGER,
    return_pct REAL,
    analysis_date TIMESTAMP,
    diagnostics TEXT,
    created_at TIMESTAMP
);

-- ========== 决策信号系统 ==========

CREATE TABLE IF NOT EXISTS decision_signals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    market VARCHAR(10),
    source_type VARCHAR(30),
    source_agent VARCHAR(50),
    source_report_id INTEGER,
    trace_id VARCHAR(50),
    market_phase VARCHAR(30),
    trigger_source VARCHAR(50),
    action VARCHAR(20),
    action_label VARCHAR(50),
    confidence REAL,
    score INTEGER,
    horizon VARCHAR(20),
    entry_low REAL,
    entry_high REAL,
    stop_loss REAL,
    target_price REAL,
    invalidation TEXT,
    watch_conditions TEXT,
    reason TEXT,
    risk_summary TEXT,
    catalyst_summary TEXT,
    evidence TEXT,
    data_quality_summary TEXT,
    plan_quality VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    expires_at TIMESTAMP,
    metadata TEXT,
    report_language VARCHAR(10) DEFAULT 'zh',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS decision_signal_outcomes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    signal_id INTEGER NOT NULL,
    horizon VARCHAR(20),
    engine_version VARCHAR(20),
    eval_status VARCHAR(20),
    outcome VARCHAR(20),
    return_pct REAL,
    entry_price REAL,
    exit_price REAL,
    actual_movement VARCHAR(10),
    direction_expected VARCHAR(10),
    unable_reason VARCHAR(100),
    evaluated_at TIMESTAMP,
    created_at TIMESTAMP,
    FOREIGN KEY (signal_id) REFERENCES decision_signals(id)
);

CREATE TABLE IF NOT EXISTS decision_signal_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    signal_id INTEGER NOT NULL UNIQUE,
    feedback_value VARCHAR(20),
    reason_code VARCHAR(50),
    note TEXT,
    source VARCHAR(30),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (signal_id) REFERENCES decision_signals(id)
);

-- ========== 投资组合系统 ==========

CREATE TABLE IF NOT EXISTS portfolio_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    broker VARCHAR(50),
    market VARCHAR(10),
    base_currency VARCHAR(10) DEFAULT 'CNY',
    owner_id VARCHAR(50),
    is_active INTEGER DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS portfolio_positions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    market VARCHAR(10),
    quantity INTEGER,
    cost_price REAL,
    current_price REAL,
    profit_loss REAL,
    profit_loss_pct REAL,
    market_value REAL,
    position_pct REAL,
    buy_date TIMESTAMP,
    stop_loss_price REAL,
    target_price REAL,
    tags VARCHAR(200),
    note TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

CREATE TABLE IF NOT EXISTS portfolio_trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity REAL NOT NULL,
    price REAL NOT NULL,
    fee REAL DEFAULT 0,
    tax REAL DEFAULT 0,
    market VARCHAR(10),
    currency VARCHAR(10) DEFAULT 'CNY',
    trade_uid VARCHAR(50),
    note TEXT,
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

CREATE TABLE IF NOT EXISTS cash_ledger (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    event_date DATE NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount REAL NOT NULL,
    currency VARCHAR(10) DEFAULT 'CNY',
    note TEXT,
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

CREATE TABLE IF NOT EXISTS corporate_actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    market VARCHAR(10),
    currency VARCHAR(10) DEFAULT 'CNY',
    cash_dividend_per_share REAL,
    split_ratio REAL,
    note TEXT,
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES portfolio_accounts(id)
);

-- ========== 行情数据 ==========

CREATE TABLE IF NOT EXISTS stock_daily_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    trade_date DATE NOT NULL,
    open_price REAL,
    high_price REAL,
    low_price REAL,
    close_price REAL,
    volume INTEGER,
    amount REAL,
    change_pct REAL,
    change_amount REAL,
    turnover_rate REAL,
    amplitude REAL,
    data_source VARCHAR(30),
    created_at TIMESTAMP
);

-- ========== 自选股 ==========

CREATE TABLE IF NOT EXISTS watchlist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    market VARCHAR(10),
    added_at TIMESTAMP
);

-- ========== AI对话系统 ==========

CREATE TABLE IF NOT EXISTS chat_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200),
    message_count INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    last_active TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(50) NOT NULL,
    message_id VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    skill_name VARCHAR(50),
    created_at TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
);

-- ========== LLM用量统计 ==========

CREATE TABLE IF NOT EXISTS llm_usage_daily (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usage_date DATE NOT NULL,
    model VARCHAR(100),
    provider VARCHAR(50),
    request_count INTEGER DEFAULT 0,
    prompt_tokens INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    total_cost REAL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- ========== 智能选股 ==========

CREATE TABLE IF NOT EXISTS alphasift_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id VARCHAR(50) NOT NULL UNIQUE,
    market VARCHAR(10) DEFAULT 'cn',
    strategy VARCHAR(50),
    max_results INTEGER DEFAULT 10,
    status VARCHAR(20) DEFAULT 'pending',
    progress INTEGER DEFAULT 0,
    result TEXT,
    error_message TEXT,
    created_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- ========== 异步分析任务 ==========

CREATE TABLE IF NOT EXISTS analysis_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id VARCHAR(50) NOT NULL UNIQUE,
    stock_code VARCHAR(20),
    stock_codes TEXT,
    task_type VARCHAR(20) DEFAULT 'analysis',
    status VARCHAR(20) DEFAULT 'pending',
    progress INTEGER DEFAULT 0,
    message TEXT,
    result TEXT,
    error_message TEXT,
    skills TEXT,
    analysis_phase VARCHAR(20),
    report_language VARCHAR(10) DEFAULT 'zh',
    created_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
