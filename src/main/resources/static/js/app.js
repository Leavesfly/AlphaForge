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
