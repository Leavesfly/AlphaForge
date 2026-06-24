-- 股票智能分析系统 - SQLite 表结构初始化

CREATE TABLE IF NOT EXISTS alert_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    alert_type VARCHAR(30) NOT NULL,
    threshold_value REAL,
    condition_expr TEXT,
    enabled INTEGER DEFAULT 1,
    triggered INTEGER DEFAULT 0,
    last_triggered_at TIMESTAMP,
    one_shot INTEGER DEFAULT 0,
    notify_channels VARCHAR(200),
    note VARCHAR(500),
    created_at TIMESTAMP
);

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
    created_at TIMESTAMP
);

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
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS decision_signals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    signal_type VARCHAR(20),
    strength INTEGER,
    source VARCHAR(30),
    target_price REAL,
    stop_loss_price REAL,
    trigger_price REAL,
    position_pct REAL,
    reasoning TEXT,
    confidence REAL,
    status VARCHAR(20) DEFAULT 'active',
    valid_until TIMESTAMP,
    report_id INTEGER,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS portfolio_positions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
    updated_at TIMESTAMP
);

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
