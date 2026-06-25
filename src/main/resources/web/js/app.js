// ===== Theme Toggle =====
const themeToggle = document.getElementById('theme-toggle');
const html = document.documentElement;

// Load saved theme
const savedTheme = (() => { try { return localStorage.getItem('stockai-theme'); } catch(e) { return null; } })();
if (savedTheme) html.setAttribute('data-theme', savedTheme);
updateThemeIcons();

themeToggle.addEventListener('click', () => {
  const current = html.getAttribute('data-theme');
  const next = current === 'dark' ? 'light' : 'dark';
  html.setAttribute('data-theme', next);
  try { localStorage.setItem('stockai-theme', next); } catch(e) {}
  updateThemeIcons();
  // Re-init charts with new theme
  document.querySelectorAll('.chart-box, .chart-box-sm').forEach(el => { el._inited = false; });
  chartsInitialized = {};
  setTimeout(initCharts, 100);
  showToast(next === 'dark' ? '已切换为深色模式' : '已切换为浅色模式', 'info');
});

function updateThemeIcons() {
  const isDark = html.getAttribute('data-theme') === 'dark';
  document.getElementById('theme-icon-dark').style.display = isDark ? 'block' : 'none';
  document.getElementById('theme-icon-light').style.display = isDark ? 'none' : 'block';
}

// ===== Navigation =====
function navigateTo(page) {
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  const navItem = document.querySelector(`.nav-item[data-page="${page}"]`);
  const pageEl = document.getElementById('page-' + page);
  if (navItem) navItem.classList.add('active');
  if (pageEl) pageEl.classList.add('active');
  setTimeout(initCharts, 150);
}
document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', () => navigateTo(item.dataset.page));
});

// ===== Tab System =====
document.querySelectorAll('.tabs[data-tab-group]').forEach(tabGroup => {
  const group = tabGroup.dataset.tabGroup;
  tabGroup.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      tabGroup.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      document.querySelectorAll(`.tab-panel[data-group="${group}"]`).forEach(p => p.classList.remove('active'));
      const panel = document.querySelector(`.tab-panel[data-panel="${tab.dataset.tab}"][data-group="${group}"]`);
      if (panel) panel.classList.add('active');
      setTimeout(initCharts, 100);
    });
  });
});

// Signal filter tabs
document.querySelectorAll('#signal-tabs .tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('#signal-tabs .tab').forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    const filter = tab.dataset.filter;
    document.querySelectorAll('#signals-grid .signal-card').forEach(card => {
      if (filter === 'all') { card.style.display = ''; }
      else if (filter === 'active') { card.style.display = card.dataset.status === 'active' ? '' : 'none'; }
      else if (filter === 'verified') { card.style.display = card.dataset.status === 'verified' ? '' : 'none'; }
      else if (filter === 'expired') { card.style.display = card.dataset.status === 'expired' ? '' : 'none'; }
    });
  });
});

// ===== Toast Notifications =====
function showToast(message, type='info') {
  const container = document.getElementById('toast-container');
  const icons = { success: '✓', error: '✗', info: 'ℹ', warning: '⚠' };
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span>${icons[type] || ''}</span> ${message}`;
  container.appendChild(toast);
  requestAnimationFrame(() => toast.classList.add('show'));
  setTimeout(() => { toast.classList.remove('show'); setTimeout(() => toast.remove(), 300); }, 3000);
}

// ===== Modal =====
function openModal(id) { document.getElementById(id).classList.add('show'); }
function closeModal(id) { document.getElementById(id).classList.remove('show'); }

// ===== Analysis Runner =====
document.getElementById('btn-run-analysis').addEventListener('click', () => {
  const input = document.getElementById('analysis-input').value.trim();
  if (!input) { showToast('请输入股票代码或名称', 'warning'); return; }
  const progressEl = document.getElementById('analysis-progress');
  const bar = document.getElementById('analysis-progress-bar');
  const stepText = document.getElementById('analysis-step-text');
  const statusText = document.getElementById('analysis-status-text');
  progressEl.style.display = 'block';
  bar.style.width = '0%';
  statusText.textContent = `正在分析 ${input}...`;
  const steps = ['初始化分析引擎...', '获取行情数据...', '技术面分析中...', '基本面分析中...', '舆情数据采集...', '生成综合评估报告...', '分析完成！'];
  let i = 0;
  const timer = setInterval(() => {
    i++;
    if (i < steps.length) { bar.style.width = Math.round((i / (steps.length-1)) * 100) + '%'; stepText.textContent = steps[i]; }
    if (i >= steps.length) { clearInterval(timer); bar.classList.remove('animated'); statusText.textContent = `${input} 分析完成`; showToast(`${input} 分析报告已生成`, 'success'); }
  }, 800);
});

// ===== Signal Detail Modal =====
function openSignalDetail(name, code, action) {
  document.getElementById('signal-detail-title').textContent = `${name} (${code}) 信号详情`;
  const actionLabels = { strong_buy: '强买入', buy: '买入', sell: '卖出', hold: '持有' };
  const actionColors = { strong_buy: 'success', buy: 'success', sell: 'danger', hold: 'warning' };
  document.getElementById('signal-detail-content').innerHTML = `
    <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap">
      <span class="badge badge-${actionColors[action]}" style="font-size:13px;padding:5px 14px">${actionLabels[action]}</span>
      <span class="badge badge-info" style="font-size:12px;padding:4px 10px">置信度 87%</span>
      <span class="badge badge-primary" style="font-size:12px;padding:4px 10px">中期</span>
    </div>
    <div style="margin-bottom:16px">
      <div style="font-size:13px;color:var(--text-muted);line-height:1.7">
        <strong style="color:var(--text)">推荐理由：</strong>基本面强劲（ROE>25%），技术面突破关键阻力位，放量突破20日均线。催化剂：年报超预期+机构增持。
      </div>
    </div>
    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px">
      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px"><div style="font-size:10px;color:var(--text-dim);text-transform:uppercase">入场下限</div><div style="font-size:16px;font-weight:600;margin-top:4px">1680</div></div>
      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px"><div style="font-size:10px;color:var(--text-dim);text-transform:uppercase">入场上限</div><div style="font-size:16px;font-weight:600;margin-top:4px">1720</div></div>
      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px"><div style="font-size:10px;color:var(--text-dim);text-transform:uppercase">止损</div><div style="font-size:16px;font-weight:600;margin-top:4px;color:var(--danger)">1620</div></div>
      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px"><div style="font-size:10px;color:var(--text-dim);text-transform:uppercase">目标价</div><div style="font-size:16px;font-weight:600;margin-top:4px;color:var(--success)">1900</div></div>
    </div>
    <div style="margin-bottom:12px">
      <strong style="font-size:12px;color:var(--text-dim)">风险提示：</strong>
      <span style="font-size:12px;color:var(--text-muted)">若跌破1620元止损位，信号失效。注意大盘系统性风险。</span>
    </div>
    <div>
      <strong style="font-size:12px;color:var(--text-dim)">生成信息：</strong>
      <span style="font-size:12px;color:var(--text-muted)">多Agent协作模式 | qwen-max | 2024-03-15 09:32 | Token: 4,521</span>
    </div>
  `;
  openModal('modal-signal');
}

// ===== Chat System =====
const chatMessages = document.getElementById('chat-messages');
const chatInput = document.getElementById('chat-input');

chatInput.addEventListener('input', function() { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 120) + 'px'; });
chatInput.addEventListener('keydown', function(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChatMessage(); } });

function sendChatMessage() {
  const text = chatInput.value.trim();
  if (!text) return;
  appendMessage('user', text);
  chatInput.value = ''; chatInput.style.height = 'auto';
  const typingEl = document.createElement('div');
  typingEl.className = 'chat-msg assistant';
  typingEl.id = 'typing-indicator';
  typingEl.innerHTML = `<div class="chat-avatar">AI</div><div><div class="chat-bubble"><div class="typing-indicator"><span></span><span></span><span></span></div></div></div>`;
  chatMessages.appendChild(typingEl);
  chatMessages.scrollTop = chatMessages.scrollHeight;
  setTimeout(() => {
    typingEl.remove();
    const responses = [
      `好的，我来帮你分析一下。根据最新行情数据：\n\n该标的近期走势呈现企稳回升态势，成交量较前期有所放大，技术指标显示短期动能转强。建议关注上方压力位的突破情况。\n\n需要我给出具体的入场建议吗？`,
      `收到！让我查看一下相关数据...\n\n从基本面看，公司最新一季度营收同比增长15.3%，净利润率保持稳定。行业景气度维持高位，机构近期有增持迹象。\n\n技术面上，MACD 即将形成金叉，RSI处于中性区域，仍有上行空间。`,
      `这是一个很好的问题。根据我的分析：\n\n当前市场处于震荡格局，建议采取分批建仓策略。核心仓位可配置稳健型标的，卫星仓位关注弹性品种。\n\n具体个股方面，我建议关注 AI 算力、红利低波两个方向。需要我推荐具体标的吗？`
    ];
    appendMessage('assistant', responses[Math.floor(Math.random() * responses.length)]);
  }, 1500 + Math.random() * 1000);
}

function appendMessage(role, text) {
  const time = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  const avatar = role === 'user' ? 'Y' : 'AI';
  const msg = document.createElement('div');
  msg.className = `chat-msg ${role}`;
  msg.innerHTML = `<div class="chat-avatar">${avatar}</div><div><div class="chat-bubble">${text.replace(/\n/g, '<br>')}</div><div class="chat-time">${time}</div></div>`;
  chatMessages.appendChild(msg);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Chat session switching
document.querySelectorAll('.chat-session-item').forEach(item => {
  item.addEventListener('click', () => {
    document.querySelectorAll('.chat-session-item').forEach(s => s.classList.remove('active'));
    item.classList.add('active');
    showToast(`已切换到：${item.querySelector('.session-title').textContent}`, 'info');
  });
});

function newChatSession() {
  const list = document.getElementById('chat-session-list');
  const newItem = document.createElement('div');
  newItem.className = 'chat-session-item active';
  newItem.innerHTML = `<div class="session-title">新对话</div><div class="session-meta"><span>0条消息</span><span>刚刚</span></div>`;
  list.querySelectorAll('.chat-session-item').forEach(s => s.classList.remove('active'));
  list.prepend(newItem);
  chatMessages.innerHTML = '';
  appendMessage('assistant', '你好！新对话已创建。有什么我可以帮你分析的吗？');
  newItem.addEventListener('click', () => {
    list.querySelectorAll('.chat-session-item').forEach(s => s.classList.remove('active'));
    newItem.classList.add('active');
  });
}

// ===== Watchlist =====
function addWatchlist() {
  const input = document.getElementById('watchlist-input');
  const code = input.value.trim();
  if (!code) { showToast('请输入股票代码', 'warning'); return; }
  showToast(`${code} 已添加到自选股`, 'success');
  input.value = '';
}
function removeWatchlistRow(btn) {
  const row = btn.closest('tr');
  const name = row.querySelector('strong').textContent;
  row.style.opacity = '0'; row.style.transform = 'translateX(20px)'; row.style.transition = '0.3s';
  setTimeout(() => row.remove(), 300);
  showToast(`${name} 已从自选股移除`, 'warning');
}

// ===== Screening =====
function runScreening() {
  showToast('扫描任务已启动...', 'info');
  const card = document.getElementById('screening-progress-card');
  const bar = card.querySelector('.progress-bar');
  bar.style.width = '0%'; bar.classList.add('animated');
  card.querySelector('.badge').textContent = '运行中'; card.querySelector('.badge').className = 'badge badge-warning';
  let progress = 0;
  const timer = setInterval(() => {
    progress += Math.random() * 15;
    if (progress >= 100) { progress = 100; clearInterval(timer); bar.classList.remove('animated'); card.querySelector('.badge').textContent = '已完成'; card.querySelector('.badge').className = 'badge badge-success'; showToast('扫描完成，找到5只潜力标的', 'success'); }
    bar.style.width = progress + '%';
  }, 400);
}

// ===== Global Search Shortcut =====
document.addEventListener('keydown', (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    document.getElementById('global-search').focus();
  }
});
