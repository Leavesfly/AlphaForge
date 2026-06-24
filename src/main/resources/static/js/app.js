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
