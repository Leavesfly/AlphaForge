/**
 * 股票智能分析系统 - 核心JS工具库
 * 提供API请求、DOM工具、通知、格式化、i18n、图表等基础能力
 */

// ========== 全局配置 ==========
const API_BASE = '/api/v1';
const APP = {
    theme: localStorage.getItem('dsa-theme') || 'light',
    language: localStorage.getItem('dsa-lang') || 'zh',
};

// ========== i18n 国际化 ==========
const I18N = {
    zh: {
        'nav.dashboard': '仪表盘', 'nav.analysis': '股票分析', 'nav.chat': 'AI问股',
        'nav.screening': '智能选股', 'nav.portfolio': '投资组合', 'nav.signals': '决策信号',
        'nav.backtest': '回测分析', 'nav.alerts': '告警管理', 'nav.usage': 'Token统计',
        'nav.history': '分析历史', 'nav.settings': '系统设置', 'nav.theme': '切换主题',
        'nav.logout': '退出登录',
        'common.loading': '加载中...', 'common.noData': '暂无数据', 'common.confirm': '确认',
        'common.cancel': '取消', 'common.delete': '删除', 'common.submit': '提交',
        'common.search': '搜索', 'common.filter': '筛选', 'common.export': '导出',
        'common.import': '导入', 'common.refresh': '刷新', 'common.total': '共',
        'common.items': '条', 'common.send': '发送', 'common.generating': '生成中...',
        'dashboard.title': '仪表盘', 'dashboard.marketSentiment': '市场情绪',
        'dashboard.positions': '持仓数量', 'dashboard.alerts': '活跃告警',
        'dashboard.pnl': '总盈亏', 'dashboard.watchlist': '自选股',
        'dashboard.tasks': '分析任务', 'dashboard.recent': '最近分析',
        'dashboard.analyze': '开始分析', 'dashboard.marketReview': '大盘复盘',
        'portfolio.title': '投资组合', 'portfolio.positions': '持仓概览',
        'portfolio.trades': '交易记录', 'portfolio.cash': '现金流水',
        'portfolio.corporate': '企业事件', 'portfolio.import': 'CSV导入',
        'portfolio.addTrade': '新增交易', 'portfolio.allAccounts': '全部账户',
        'backtest.title': '回测分析', 'backtest.run': '运行回测',
        'backtest.winRate': '总胜率', 'backtest.totalSignals': '总信号数',
        'backtest.plRatio': '盈亏比', 'backtest.avgReturn': '平均收益',
        'screening.title': '智能选股', 'screening.start': '开始选股',
        'screening.hotspots': '热点追踪', 'screening.results': '选股结果',
        'chat.title': 'AI问股', 'chat.newSession': '新对话', 'chat.placeholder': '输入问题，如: 用缠论分析茅台',
        'chat.skills': '技能', 'chat.export': '导出',
    },
    en: {
        'nav.dashboard': 'Dashboard', 'nav.analysis': 'Analysis', 'nav.chat': 'AI Chat',
        'nav.screening': 'Screening', 'nav.portfolio': 'Portfolio', 'nav.signals': 'Signals',
        'nav.backtest': 'Backtest', 'nav.alerts': 'Alerts', 'nav.usage': 'Token Usage',
        'nav.history': 'History', 'nav.settings': 'Settings', 'nav.theme': 'Toggle Theme',
        'nav.logout': 'Logout',
        'common.loading': 'Loading...', 'common.noData': 'No data', 'common.confirm': 'Confirm',
        'common.cancel': 'Cancel', 'common.delete': 'Delete', 'common.submit': 'Submit',
        'common.search': 'Search', 'common.filter': 'Filter', 'common.export': 'Export',
        'common.import': 'Import', 'common.refresh': 'Refresh', 'common.total': 'Total',
        'common.items': 'items', 'common.send': 'Send', 'common.generating': 'Generating...',
        'dashboard.title': 'Dashboard', 'dashboard.marketSentiment': 'Market Sentiment',
        'dashboard.positions': 'Positions', 'dashboard.alerts': 'Active Alerts',
        'dashboard.pnl': 'Total P&L', 'dashboard.watchlist': 'Watchlist',
        'dashboard.tasks': 'Analysis Tasks', 'dashboard.recent': 'Recent Analysis',
        'dashboard.analyze': 'Analyze', 'dashboard.marketReview': 'Market Review',
        'portfolio.title': 'Portfolio', 'portfolio.positions': 'Positions',
        'portfolio.trades': 'Trades', 'portfolio.cash': 'Cash Ledger',
        'portfolio.corporate': 'Corporate Actions', 'portfolio.import': 'CSV Import',
        'portfolio.addTrade': 'Add Trade', 'portfolio.allAccounts': 'All Accounts',
        'backtest.title': 'Backtest', 'backtest.run': 'Run Backtest',
        'backtest.winRate': 'Win Rate', 'backtest.totalSignals': 'Total Signals',
        'backtest.plRatio': 'P/L Ratio', 'backtest.avgReturn': 'Avg Return',
        'screening.title': 'Screening', 'screening.start': 'Start Screening',
        'screening.hotspots': 'Hotspots', 'screening.results': 'Results',
        'chat.title': 'AI Chat', 'chat.newSession': 'New Chat', 'chat.placeholder': 'Ask about stocks, e.g. Analyze AAPL trend',
        'chat.skills': 'Skills', 'chat.export': 'Export',
    }
};

function t(key) { return I18N[APP.language]?.[key] || I18N.zh[key] || key; }

function applyI18n() {
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        el.textContent = t(key);
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
        el.placeholder = t(el.getAttribute('data-i18n-placeholder'));
    });
    const langLabel = document.getElementById('langLabel');
    if (langLabel) langLabel.textContent = APP.language === 'zh' ? 'EN' : '中';
}

function toggleLanguage() {
    APP.language = APP.language === 'zh' ? 'en' : 'zh';
    localStorage.setItem('dsa-lang', APP.language);
    applyI18n();
}

// ========== API请求封装 ==========
async function api(path, options = {}) {
    const url = API_BASE + path;
    const config = {
        headers: { 'Content-Type': 'application/json', ...options.headers },
        credentials: 'include',
        ...options,
    };
    try {
        const res = await fetch(url, config);
        if (res.status === 401) {
            if (!window.location.pathname.includes('/login')) {
                window.location.href = '/web/login?redirect=' + encodeURIComponent(window.location.pathname);
            }
            throw new Error('未授权');
        }
        if (!res.ok) {
            const errorData = await res.json().catch(() => ({}));
            const err = new Error(errorData.message || errorData.error || `HTTP ${res.status}`);
            err.status = res.status;
            err.data = errorData;
            throw err;
        }
        if (res.status === 204) return null;
        return await res.json();
    } catch (e) {
        if (!e.status) console.error('API请求失败:', path, e);
        throw e;
    }
}

api.get = (path, params) => {
    const query = params ? '?' + new URLSearchParams(params).toString() : '';
    return api(path + query);
};
api.post = (path, body) => api(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
api.put = (path, body) => api(path, { method: 'PUT', body: JSON.stringify(body) });
api.patch = (path, body) => api(path, { method: 'PATCH', body: JSON.stringify(body) });
api.delete = (path) => api(path, { method: 'DELETE' });
api.upload = (path, formData) => api(path, { method: 'POST', body: formData, headers: {} });

// ========== SSE 封装 ==========
function createSSE(url, handlers = {}) {
    const eventSource = new EventSource(url, { withCredentials: true });
    if (handlers.onMessage) eventSource.onmessage = (e) => handlers.onMessage(e.data);
    if (handlers.onError) eventSource.onerror = handlers.onError;
    if (handlers.events) {
        Object.entries(handlers.events).forEach(([event, handler]) => {
            eventSource.addEventListener(event, (e) => handler(e.data));
        });
    }
    return eventSource;
}

// ========== Toast 通知 ==========
function showToast(message, type = 'info', duration = 3000) {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, duration);
}

// ========== DOM 工具 ==========
function $(selector, parent = document) { return parent.querySelector(selector); }
function $$(selector, parent = document) { return [...parent.querySelectorAll(selector)]; }

function el(tag, attrs = {}, children = []) {
    const element = document.createElement(tag);
    Object.entries(attrs).forEach(([key, value]) => {
        if (key === 'className') element.className = value;
        else if (key === 'innerHTML') element.innerHTML = value;
        else if (key === 'textContent') element.textContent = value;
        else if (key.startsWith('on')) element.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'style' && typeof value === 'object') Object.assign(element.style, value);
        else element.setAttribute(key, value);
    });
    children.forEach(child => {
        if (typeof child === 'string') element.appendChild(document.createTextNode(child));
        else if (child) element.appendChild(child);
    });
    return element;
}

function clearElement(element) { element.innerHTML = ''; return element; }

// ========== 格式化工具 ==========
function formatDate(dateStr) {
    if (!dateStr) return '--';
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}
function formatDateShort(dateStr) {
    if (!dateStr) return '--';
    const d = new Date(dateStr);
    return `${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}
function formatNumber(num, decimals = 2) {
    if (num == null || isNaN(num)) return '--';
    return Number(num).toFixed(decimals);
}
function formatPct(num) {
    if (num == null || isNaN(num)) return '--';
    return Number(num).toFixed(2) + '%';
}
function formatMoney(num, currency = '¥') {
    if (num == null || isNaN(num)) return '--';
    return currency + Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ========== 分页组件 ==========
function renderPagination(container, { page, total, pageSize = 20, onChange }) {
    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) { container.innerHTML = ''; return; }
    container.innerHTML = '';
    container.className = 'pagination';
    const addBtn = (text, p, active = false) => {
        const btn = el('button', { className: 'page-btn' + (active ? ' active' : ''), textContent: text });
        if (p !== page && p >= 1 && p <= totalPages) btn.onclick = () => onChange(p);
        else if (p === page) btn.disabled = true;
        container.appendChild(btn);
    };
    addBtn('‹', page - 1);
    const start = Math.max(1, page - 2);
    const end = Math.min(totalPages, page + 2);
    for (let i = start; i <= end; i++) addBtn(String(i), i, i === page);
    addBtn('›', page + 1);
    container.appendChild(el('span', { className: 'page-info', textContent: `${t('common.total')} ${total} ${t('common.items')}` }));
}

// ========== 确认对话框 ==========
function confirmDialog(message) {
    return new Promise(resolve => {
        const overlay = el('div', { className: 'modal-overlay open' });
        const modal = el('div', { className: 'modal' }, [
            el('div', { className: 'modal-header' }, [el('h3', { textContent: t('common.confirm') })]),
            el('div', { className: 'modal-body' }, [el('p', { textContent: message })]),
            el('div', { className: 'modal-footer' }, [
                el('button', { className: 'btn btn-secondary', textContent: t('common.cancel'), onClick: () => { overlay.remove(); resolve(false); } }),
                el('button', { className: 'btn btn-danger', textContent: t('common.confirm'), onClick: () => { overlay.remove(); resolve(true); } }),
            ]),
        ]);
        overlay.appendChild(modal);
        overlay.onclick = (e) => { if (e.target === overlay) { overlay.remove(); resolve(false); } };
        document.body.appendChild(overlay);
    });
}

// ========== Drawer 工具 ==========
function openDrawer(title, contentFn) {
    let overlay = $('.drawer-overlay');
    let drawer = $('.drawer');
    if (!overlay) {
        overlay = el('div', { className: 'drawer-overlay' });
        drawer = el('div', { className: 'drawer' });
        document.body.appendChild(overlay);
        document.body.appendChild(drawer);
    }
    drawer.innerHTML = `<div class="drawer-header"><h3>${title}</h3><button class="modal-close" onclick="closeDrawer()">&times;</button></div><div class="drawer-body"></div>`;
    if (contentFn) contentFn($('.drawer-body', drawer));
    setTimeout(() => { overlay.classList.add('open'); drawer.classList.add('open'); }, 10);
    overlay.onclick = closeDrawer;
}
function closeDrawer() {
    const overlay = $('.drawer-overlay');
    const drawer = $('.drawer');
    if (overlay) overlay.classList.remove('open');
    if (drawer) drawer.classList.remove('open');
}

// ========== Tab 切换 ==========
function initTabs(container) {
    const tabs = $$(`.tab-item`, container);
    const contents = $$('.tab-content', container);
    tabs.forEach(tab => {
        tab.onclick = () => {
            tabs.forEach(t => t.classList.remove('active'));
            contents.forEach(c => c.classList.remove('active'));
            tab.classList.add('active');
            const target = $(tab.dataset.target || `#${tab.dataset.tab}`, container);
            if (target) target.classList.add('active');
        };
    });
}

// ========== 主题切换 ==========
function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('dsa-theme', next);
}

// ========== Chart.js 工具 ==========
const CHART_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16'];

function createPieChart(canvasId, labels, data, title) {
    const ctx = document.getElementById(canvasId);
    if (!ctx || typeof Chart === 'undefined') return null;
    return new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: CHART_COLORS.slice(0, data.length),
                borderWidth: 2,
                borderColor: getComputedStyle(document.documentElement).getPropertyValue('--card-bg').trim() || '#fff',
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, padding: 12, font: { size: 11 } } },
                title: title ? { display: true, text: title, font: { size: 14, weight: '600' } } : { display: false } }
        }
    });
}

function createLineChart(canvasId, labels, datasets, title) {
    const ctx = document.getElementById(canvasId);
    if (!ctx || typeof Chart === 'undefined') return null;
    const chartDatasets = datasets.map((ds, i) => ({
        label: ds.label,
        data: ds.data,
        borderColor: CHART_COLORS[i % CHART_COLORS.length],
        backgroundColor: CHART_COLORS[i % CHART_COLORS.length] + '20',
        borderWidth: 2, tension: 0.3, fill: ds.fill || false, pointRadius: 2,
    }));
    return new Chart(ctx, {
        type: 'line',
        data: { labels, datasets: chartDatasets },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'top', labels: { boxWidth: 12, font: { size: 11 } } },
                title: title ? { display: true, text: title, font: { size: 14, weight: '600' } } : { display: false } },
            scales: { y: { beginAtZero: false, grid: { color: 'rgba(0,0,0,.05)' } }, x: { grid: { display: false } } }
        }
    });
}

function createBarChart(canvasId, labels, data, title) {
    const ctx = document.getElementById(canvasId);
    if (!ctx || typeof Chart === 'undefined') return null;
    return new Chart(ctx, {
        type: 'bar',
        data: {
            labels,
            datasets: [{ data, backgroundColor: CHART_COLORS.slice(0, data.length), borderRadius: 4 }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false },
                title: title ? { display: true, text: title, font: { size: 14, weight: '600' } } : { display: false } },
            scales: { y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,.05)' } }, x: { grid: { display: false } } }
        }
    });
}

// ========== 页面初始化 ==========
document.addEventListener('DOMContentLoaded', () => {
    // 恢复主题
    const savedTheme = localStorage.getItem('dsa-theme');
    if (savedTheme) document.documentElement.setAttribute('data-theme', savedTheme);

    // 恢复语言
    APP.language = localStorage.getItem('dsa-lang') || 'zh';
    applyI18n();

    // 高亮当前导航
    const path = window.location.pathname;
    $$('.nav-list a').forEach(a => {
        if (a.getAttribute('href') === path) a.classList.add('active');
    });

    // 初始化所有Tab
    $$('[data-tabs]').forEach(initTabs);
});
/**
 * 股票智能分析系统 - 核心JS工具库
 * 提供API请求、DOM工具、通知、格式化等基础能力
 */

// ========== 全局配置 ==========
const API_BASE = '/api/v1';
const APP = {
    theme: localStorage.getItem('dsa-theme') || 'light',
    language: localStorage.getItem('dsa-lang') || 'zh',
};

// ========== API请求封装 ==========
async function api(path, options = {}) {
    const url = API_BASE + path;
    const config = {
        headers: { 'Content-Type': 'application/json', ...options.headers },
        credentials: 'include',
        ...options,
    };
    try {
        const res = await fetch(url, config);
        if (res.status === 401) {
            if (!window.location.pathname.includes('/login')) {
                window.location.href = '/web/login?redirect=' + encodeURIComponent(window.location.pathname);
            }
            throw new Error('未授权');
        }
        if (!res.ok) {
            const errorData = await res.json().catch(() => ({}));
            const err = new Error(errorData.message || errorData.error || `HTTP ${res.status}`);
            err.status = res.status;
            err.data = errorData;
            throw err;
        }
        if (res.status === 204) return null;
        return await res.json();
    } catch (e) {
        if (!e.status) console.error('API请求失败:', path, e);
        throw e;
    }
}

api.get = (path, params) => {
    const query = params ? '?' + new URLSearchParams(params).toString() : '';
    return api(path + query);
};
api.post = (path, body) => api(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
api.put = (path, body) => api(path, { method: 'PUT', body: JSON.stringify(body) });
api.patch = (path, body) => api(path, { method: 'PATCH', body: JSON.stringify(body) });
api.delete = (path) => api(path, { method: 'DELETE' });
api.upload = (path, formData) => api(path, { method: 'POST', body: formData, headers: {} });

// ========== SSE 封装 ==========
function createSSE(url, handlers = {}) {
    const eventSource = new EventSource(url, { withCredentials: true });
    if (handlers.onMessage) eventSource.onmessage = (e) => handlers.onMessage(e.data);
    if (handlers.onError) eventSource.onerror = handlers.onError;
    if (handlers.events) {
        Object.entries(handlers.events).forEach(([event, handler]) => {
            eventSource.addEventListener(event, (e) => handler(e.data));
        });
    }
    return eventSource;
}

// ========== Toast 通知 ==========
function showToast(message, type = 'info', duration = 3000) {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, duration);
}

// ========== DOM 工具 ==========
function $(selector, parent = document) { return parent.querySelector(selector); }
function $$(selector, parent = document) { return [...parent.querySelectorAll(selector)]; }

function el(tag, attrs = {}, children = []) {
    const element = document.createElement(tag);
    Object.entries(attrs).forEach(([key, value]) => {
        if (key === 'className') element.className = value;
        else if (key === 'innerHTML') element.innerHTML = value;
        else if (key === 'textContent') element.textContent = value;
        else if (key.startsWith('on')) element.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'style' && typeof value === 'object') Object.assign(element.style, value);
        else element.setAttribute(key, value);
    });
    children.forEach(child => {
        if (typeof child === 'string') element.appendChild(document.createTextNode(child));
        else if (child) element.appendChild(child);
    });
    return element;
}

function clearElement(element) { element.innerHTML = ''; return element; }

// ========== 格式化工具 ==========
function formatDate(dateStr) {
    if (!dateStr) return '--';
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}
function formatDateShort(dateStr) {
    if (!dateStr) return '--';
    const d = new Date(dateStr);
    return `${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
}
function formatNumber(num, decimals = 2) {
    if (num == null || isNaN(num)) return '--';
    return Number(num).toFixed(decimals);
}
function formatPct(num) {
    if (num == null || isNaN(num)) return '--';
    return Number(num).toFixed(2) + '%';
}
function formatMoney(num, currency = '¥') {
    if (num == null || isNaN(num)) return '--';
    return currency + Number(num).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ========== 分页组件 ==========
function renderPagination(container, { page, total, pageSize = 20, onChange }) {
    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) { container.innerHTML = ''; return; }
    container.innerHTML = '';
    container.className = 'pagination';

    const addBtn = (text, p, active = false) => {
        const btn = el('button', { className: 'page-btn' + (active ? ' active' : ''), textContent: text });
        if (p !== page && p >= 1 && p <= totalPages) btn.onclick = () => onChange(p);
        else if (p === page) btn.disabled = true;
        container.appendChild(btn);
    };

    addBtn('‹', page - 1);
    const start = Math.max(1, page - 2);
    const end = Math.min(totalPages, page + 2);
    for (let i = start; i <= end; i++) addBtn(String(i), i, i === page);
    addBtn('›', page + 1);
    container.appendChild(el('span', { className: 'page-info', textContent: `共 ${total} 条` }));
}

// ========== 确认对话框 ==========
function confirmDialog(message) {
    return new Promise(resolve => {
        const overlay = el('div', { className: 'modal-overlay open' });
        const modal = el('div', { className: 'modal' }, [
            el('div', { className: 'modal-header' }, [el('h3', { textContent: '确认' })]),
            el('div', { className: 'modal-body' }, [el('p', { textContent: message })]),
            el('div', { className: 'modal-footer' }, [
                el('button', { className: 'btn btn-secondary', textContent: '取消', onClick: () => { overlay.remove(); resolve(false); } }),
                el('button', { className: 'btn btn-danger', textContent: '确认', onClick: () => { overlay.remove(); resolve(true); } }),
            ]),
        ]);
        overlay.appendChild(modal);
        overlay.onclick = (e) => { if (e.target === overlay) { overlay.remove(); resolve(false); } };
        document.body.appendChild(overlay);
    });
}

// ========== Drawer 工具 ==========
function openDrawer(title, contentFn) {
    let overlay = $('.drawer-overlay');
    let drawer = $('.drawer');
    if (!overlay) {
        overlay = el('div', { className: 'drawer-overlay' });
        drawer = el('div', { className: 'drawer' });
        document.body.appendChild(overlay);
        document.body.appendChild(drawer);
    }
    drawer.innerHTML = `<div class="drawer-header"><h3>${title}</h3><button class="modal-close" onclick="closeDrawer()">&times;</button></div><div class="drawer-body"></div>`;
    if (contentFn) contentFn($('.drawer-body', drawer));
    setTimeout(() => { overlay.classList.add('open'); drawer.classList.add('open'); }, 10);
    overlay.onclick = closeDrawer;
}
function closeDrawer() {
    const overlay = $('.drawer-overlay');
    const drawer = $('.drawer');
    if (overlay) overlay.classList.remove('open');
    if (drawer) drawer.classList.remove('open');
}

// ========== Tab 切换 ==========
function initTabs(container) {
    const tabs = $$(`.tab-item`, container);
    const contents = $$('.tab-content', container);
    tabs.forEach(tab => {
        tab.onclick = () => {
            tabs.forEach(t => t.classList.remove('active'));
            contents.forEach(c => c.classList.remove('active'));
            tab.classList.add('active');
            const target = $(tab.dataset.target || `#${tab.dataset.tab}`, container);
            if (target) target.classList.add('active');
        };
    });
}

// ========== 主题切换 ==========
function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('dsa-theme', next);
}

// ========== 页面初始化 ==========
document.addEventListener('DOMContentLoaded', () => {
    // 恢复主题
    const savedTheme = localStorage.getItem('dsa-theme');
    if (savedTheme) document.documentElement.setAttribute('data-theme', savedTheme);

    // 高亮当前导航
    const path = window.location.pathname;
    $$('.nav-list a').forEach(a => {
        if (a.getAttribute('href') === path) a.classList.add('active');
    });

    // 初始化所有Tab
    $$('[data-tabs]').forEach(initTabs);
});
/**
 * 股票智能分析系统 - 前端通用JS
 * 处理AJAX请求、WebSocket、通用交互
 */

// 全局配置
const API_BASE = '/api/v1';

// 通用API请求封装
async function apiRequest(path, options = {}) {
    const url = API_BASE + path;
    const config = {
        headers: { 'Content-Type': 'application/json', ...options.headers },
        ...options
    };
    try {
        const res = await fetch(url, config);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
    } catch (e) {
        console.error('API请求失败:', path, e);
        throw e;
    }
}

// 触发股票分析(异步)
async function triggerAnalysis(stockCode) {
    const data = await apiRequest('/analysis/run', {
        method: 'POST',
        body: JSON.stringify({ stocks: stockCode })
    });
    showToast('分析任务已提交: ' + stockCode);
    return data;
}

// 显示Toast通知
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    toast.style.cssText = 'position:fixed;top:20px;right:20px;padding:12px 20px;border-radius:8px;background:#1e293b;color:#fff;z-index:9999;animation:fadeIn .3s';
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// 自动刷新仪表盘数据(每60秒)
if (window.location.pathname.includes('/dashboard')) {
    setInterval(() => {
        fetch('/web/dashboard').then(r => r.text()).then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const cards = doc.querySelector('.card-grid');
            if (cards) document.querySelector('.card-grid').innerHTML = cards.innerHTML;
        }).catch(() => {});
    }, 60000);
}

// 页面加载完成
document.addEventListener('DOMContentLoaded', () => {
    // 高亮当前导航
    const path = window.location.pathname;
    document.querySelectorAll('.nav-list a').forEach(a => {
        if (a.getAttribute('href') === path) a.classList.add('active');
    });
});
