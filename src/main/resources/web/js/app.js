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
  bar.classList.add('animated');
  statusText.textContent = `正在分析 ${input}...`;
  stepText.textContent = '提交分析任务...';

  // 调用真实API
  fetch('/api/v1/analysis/run', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({stock_code: input})
  }).then(r => r.json()).then(data => {
    if (data.status === 'submitted') {
      // 模拟进度（后端异步处理）
      const steps = ['任务已提交...', '获取行情数据...', '技术面分析中...', '基本面分析中...', '舆情数据采集...', '生成综合评估报告...', '分析完成！'];
      let i = 0;
      const timer = setInterval(() => {
        i++;
        if (i < steps.length) { bar.style.width = Math.round((i / (steps.length-1)) * 100) + '%'; stepText.textContent = steps[i]; }
        if (i >= steps.length) { clearInterval(timer); bar.classList.remove('animated'); statusText.textContent = `${input} 分析完成`; showToast(`${input} 分析报告已生成`, 'success'); }
      }, 1200);
    } else {
      bar.classList.remove('animated'); statusText.textContent = '分析失败'; showToast(data.error || '分析请求失败', 'error');
    }
  }).catch(err => {
    bar.classList.remove('animated'); statusText.textContent = '分析失败'; showToast('网络错误: ' + err.message, 'error');
  });
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
let chatHistory = [];

chatInput.addEventListener('input', function() { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 120) + 'px'; });
chatInput.addEventListener('keydown', function(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChatMessage(); } });

function sendChatMessage() {
  const text = chatInput.value.trim();
  if (!text) return;
  appendMessage('user', text);
  chatHistory.push({role: 'user', content: text});
  chatInput.value = ''; chatInput.style.height = 'auto';

  // 创建AI消息占位
  const msgEl = document.createElement('div');
  msgEl.className = 'chat-msg assistant';
  msgEl.innerHTML = `<div class="chat-avatar">AI</div><div><div class="chat-bubble"><div class="typing-indicator"><span></span><span></span><span></span></div></div><div class="chat-time"></div></div>`;
  chatMessages.appendChild(msgEl);
  chatMessages.scrollTop = chatMessages.scrollHeight;

  const bubble = msgEl.querySelector('.chat-bubble');

  // 调用流式API
  fetch('/api/v1/agent/chat/stream', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({message: text, history: chatHistory.slice(-10)})
  }).then(response => {
    bubble.innerHTML = '';
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let fullText = '';
    function read() {
      reader.read().then(({done, value}) => {
        if (done) {
          chatHistory.push({role: 'assistant', content: fullText});
          msgEl.querySelector('.chat-time').textContent = new Date().toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'});
          return;
        }
        const lines = decoder.decode(value, {stream: true}).split('\n');
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.substring(5).trim();
            if (data === '[DONE]') continue;
            fullText += data;
            bubble.innerHTML = fullText.replace(/\n/g, '<br>');
            chatMessages.scrollTop = chatMessages.scrollHeight;
          }
        }
        read();
      });
    }
    read();
  }).catch(err => {
    bubble.innerHTML = '抱歉，AI服务暂时不可用: ' + err.message;
  });
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
  fetch('/api/v1/watchlist', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({stock_code: code, stock_name: code, market: 'A'})
  }).then(r => r.json()).then(() => {
    showToast(`${code} 已添加到自选股`, 'success');
    input.value = '';
    loadWatchlist();
  }).catch(() => showToast('添加失败', 'error'));
}
function removeWatchlistRow(btn) {
  const row = btn.closest('tr');
  const code = row.querySelector('td').textContent.trim();
  const name = row.querySelector('strong').textContent;
  fetch(`/api/v1/watchlist/${code}`, {method: 'DELETE'}).then(() => {
    row.style.opacity = '0'; row.style.transform = 'translateX(20px)'; row.style.transition = '0.3s';
    setTimeout(() => row.remove(), 300);
    showToast(`${name} 已从自选股移除`, 'warning');
  }).catch(() => showToast('删除失败', 'error'));
}
function loadWatchlist() {
  fetch('/api/v1/watchlist').then(r => r.json()).then(items => {
    const tbody = document.getElementById('watchlist-body');
    if (!tbody) return;
    if (!items.length) { tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">自选股列表为空，请在上方添加股票代码</td></tr>'; return; }
    tbody.innerHTML = items.map(item => `<tr><td>${item.stockCode}</td><td><strong>${item.stockName||item.stockCode}</strong></td><td>${item.market||'A股'}</td><td>-</td><td>-</td><td>-</td><td>${item.addedAt?item.addedAt.substring(0,10):'-'}</td><td><button class="btn btn-sm btn-outline" onclick="showToast('已加入分析队列','success')">分析</button> <button class="btn btn-sm btn-outline" onclick="openModal('modal-alert')">告警</button> <button class="btn btn-sm btn-ghost" onclick="removeWatchlistRow(this)" style="color:var(--danger)">删除</button></td></tr>`).join('');
  }).catch(() => {});
}
function batchAnalyzeWatchlist() {
  fetch('/api/v1/watchlist').then(r => r.json()).then(items => {
    if (!items.length) { showToast('自选股列表为空', 'warning'); return; }
    const codes = items.map(i => i.stockCode).join(',');
    fetch('/api/v1/analysis/run', {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({stock_code: codes})
    }).then(() => showToast(`批量分析已启动，共${items.length}只股票`, 'success'))
      .catch(() => showToast('批量分析启动失败', 'error'));
  }).catch(() => showToast('无法获取自选股列表', 'error'));
}

// ===== Screening =====
function runScreening() {
  showToast('扫描任务已启动...', 'info');
  const card = document.getElementById('screening-progress-card');
  const bar = card.querySelector('.progress-bar');
  bar.style.width = '0%'; bar.classList.add('animated');
  card.querySelector('.badge').textContent = '运行中'; card.querySelector('.badge').className = 'badge badge-warning';

  fetch('/api/v1/screening/run', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({strategy: 'value_growth', market: 'A', max_results: 10})
  }).then(r => r.json()).then(data => {
    bar.style.width = '100%'; bar.classList.remove('animated');
    card.querySelector('.badge').textContent = '已完成'; card.querySelector('.badge').className = 'badge badge-success';
    const count = data.results ? data.results.length : 0;
    showToast(`扫描完成，找到${count}只潜力标的`, 'success');
  }).catch(err => {
    bar.classList.remove('animated');
    card.querySelector('.badge').textContent = '失败'; card.querySelector('.badge').className = 'badge badge-danger';
    showToast('选股失败: ' + err.message, 'error');
  });
}

// ===== Global Search Shortcut =====
document.addEventListener('keydown', (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    document.getElementById('global-search').focus();
  }
});

// ===== Page Data Loading =====

/** 仪表盘加载 */
function loadDashboard() {
  fetch('/api/v1/dashboard/stats').then(r => r.json()).then(data => {
    const cards = document.querySelectorAll('#page-dashboard .stat-card');
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = data.total_reports || 0;
    if (cards[1]) cards[1].querySelector('.stat-value').textContent = data.active_signals || 0;
    // 渲染最新信号表格
    const tbody = document.querySelector('#page-dashboard table tbody');
    if (tbody) {
      if (data.recent_signals && data.recent_signals.length > 0) {
        tbody.innerHTML = data.recent_signals.slice(0, 5).map(s => {
          const signalBadge = s.action === 'buy' ? '<span class="badge badge-success">买入</span>' : s.action === 'sell' ? '<span class="badge badge-danger">卖出</span>' : '<span class="badge badge-warning">持有</span>';
          return `<tr><td><strong>${s.stockName||''}</strong> <span style="color:var(--text-dim)">${s.stockCode||''}</span></td><td>${signalBadge}</td><td>${s.confidence?Math.round(s.confidence*100)+'%':'-'}</td><td>${s.entryLow||'-'}-${s.entryHigh||'-'}</td><td>${s.stopLoss||'-'}</td><td>${s.targetPrice||'-'}</td><td>${s.createdAt?s.createdAt.substring(5,16):'-'}</td><td><span class="badge badge-info">${s.status||'active'}</span></td></tr>`;
        }).join('');
      } else {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无决策信号，请先运行股票分析</td></tr>';
      }
    }
  }).catch(() => {});
}

/** 分析历史加载 */
function loadHistory() {
  fetch('/api/v1/history?limit=20').then(r => r.json()).then(reports => {
    const tbody = document.querySelector('#page-analysis table tbody');
    if (!tbody) return;
    if (!reports.length) { tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:var(--text-dim);padding:24px">暂无分析记录，请在上方输入股票代码开始分析</td></tr>'; return; }
    tbody.innerHTML = reports.map(r => {
      const scoreCls = (r.totalScore||0) >= 70 ? 'success' : (r.totalScore||0) >= 50 ? 'info' : (r.totalScore||0) >= 30 ? 'warning' : 'danger';
      return `<tr><td><strong>${r.stockName||''}</strong> ${r.stockCode||''}</td><td><span class="badge badge-${scoreCls}">${r.totalScore||'-'}</span></td><td>${r.signal||'-'}</td><td>${r.confidence?Math.round(r.confidence*100)+'%':'-'}</td><td>${r.llmModel||'-'}</td><td>${r.tokenUsage||'-'}</td><td>${r.durationSeconds?r.durationSeconds.toFixed(1)+'s':'-'}</td><td>${r.analysisDate?r.analysisDate.substring(5,16):'-'}</td><td><button class="btn btn-sm btn-outline" onclick="showToast('报告已打开','info')">查看</button></td></tr>`;
    }).join('');
  }).catch(() => {});
}

/** 决策信号加载 */
function loadSignals() {
  fetch('/api/v1/decision-signals?pageSize=20').then(r => r.json()).then(data => {
    const items = data.items || [];
    const grid = document.getElementById('signals-grid');
    if (!grid) return;
    if (!items.length) { grid.innerHTML = '<div class="empty-state">暂无决策信号，分析股票后将自动生成</div>'; return; }
    grid.innerHTML = items.map(s => {
      const actionCls = (s.action||'').includes('buy') ? 'buy' : s.action === 'sell' ? 'sell' : 'hold';
      const actionLabel = s.action === 'buy' ? '买入' : s.action === 'strong_buy' ? '强买入' : s.action === 'sell' ? '卖出' : '持有';
      const badgeCls = actionCls === 'buy' ? 'success' : actionCls === 'sell' ? 'danger' : 'warning';
      return `<div class="card signal-card ${actionCls}" data-status="${s.status||'active'}"><div class="signal-header"><div><div class="signal-stock">${s.stockName||''}<span>${s.stockCode||''}</span></div><div style="font-size:12px;color:var(--text-muted);margin-top:4px">置信度 ${s.confidence?Math.round(s.confidence*100)+'%':'-'}</div></div><span class="badge badge-${badgeCls}">${actionLabel}</span></div><div style="font-size:13px;color:var(--text-muted);line-height:1.6;margin-bottom:4px">${s.reason||''}</div><div class="signal-meta"><div class="signal-meta-item"><div class="label">止损</div><div class="value text-down">${s.stopLoss||'-'}</div></div><div class="signal-meta-item"><div class="label">目标价</div><div class="value text-up">${s.targetPrice||'-'}</div></div><div class="signal-meta-item"><div class="label">评分</div><div class="value">${s.score||'-'}</div></div></div></div>`;
    }).join('');
  }).catch(() => {});
}

/** 用量监控加载 */
function loadUsage() {
  fetch('/api/v1/usage').then(r => r.json()).then(data => {
    const cards = document.querySelectorAll('#page-usage .stat-card');
    const today = data.today || {};
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = today.calls || 0;
    if (cards[1]) cards[1].querySelector('.stat-value').textContent = today.total_tokens ? (today.total_tokens > 1000000 ? (today.total_tokens/1000000).toFixed(1)+'M' : (today.total_tokens/1000).toFixed(0)+'K') : '0';
    if (cards[2]) cards[2].querySelector('.stat-value').textContent = today.estimated_cost || '¥0';
    const overall = data.overall || {};
    if (cards[3]) cards[3].querySelector('.stat-value').textContent = overall.total_tokens ? (overall.total_tokens > 1000000 ? '¥'+(overall.total_tokens*0.00003).toFixed(0) : '¥0') : '¥0';
  }).catch(() => {});
}

/** 告警列表加载 */
function loadAlerts() {
  fetch('/api/v1/alerts').then(r => r.json()).then(rules => {
    const tbody = document.querySelector('#page-alerts .tab-panel[data-panel="rules"] table tbody');
    if (!tbody) return;
    if (!rules.length) { tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无告警规则，点击“新建规则”添加</td></tr>'; return; }
    tbody.innerHTML = rules.map(r => {
      const statusDot = r.enabled ? (r.triggered ? 'triggered' : 'active') : 'disabled';
      const statusText = r.enabled ? (r.triggered ? '已触发' : '启用') : '已禁用';
      return `<tr><td><span class="status-dot ${statusDot}"></span>${statusText}</td><td>${r.note||'告警规则'}</td><td>${r.stockCode||'-'}</td><td>${r.alertType||'-'}</td><td>${r.alertType||''} ${r.thresholdValue||''}</td><td>${r.notifyChannels||'default'}</td><td><span class="badge badge-warning">中</span></td><td><button class="btn btn-sm btn-outline" onclick="openModal('modal-alert')">编辑</button> <button class="btn btn-sm btn-ghost" onclick="toggleAlert(${r.id},${!r.enabled})">${r.enabled?'禁用':'启用'}</button></td></tr>`;
    }).join('');
  }).catch(() => {});
}
function loadAlertTriggers() {
  fetch('/api/v1/alerts/triggers').then(r => r.json()).then(triggers => {
    const tbody = document.querySelector('#page-alerts .tab-panel[data-panel="triggers"] table tbody');
    if (!tbody) return;
    if (!triggers.length) { tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--text-dim);padding:24px">暂无触发记录</td></tr>'; return; }
    tbody.innerHTML = triggers.map(t => `<tr><td>${t.triggered_at?t.triggered_at.substring(5,16):'-'}</td><td>${t.rule_id||'-'}</td><td>${t.display_target||t.target||'-'}</td><td>${t.observed_value||'-'}</td><td>${t.threshold_value||'-'}</td><td>${t.message||'-'}</td><td><span class="badge badge-warning">${t.status||'triggered'}</span></td></tr>`).join('');
  }).catch(() => {});
}
function loadAlertNotifications() {
  fetch('/api/v1/alerts/notifications').then(r => r.json()).then(notifs => {
    const tbody = document.querySelector('#page-alerts .tab-panel[data-panel="notifications"] table tbody');
    if (!tbody) return;
    if (!notifs.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-dim);padding:24px">暂无通知记录</td></tr>'; return; }
    tbody.innerHTML = notifs.map(n => `<tr><td>${n.sent_at?n.sent_at.substring(5,16):'-'}</td><td><span class="badge badge-info">${n.channel||'-'}</span></td><td>${n.trigger_id||'-'}</td><td><span class="badge badge-${n.success?'success':'danger'}">${n.success?'成功':'失败'}</span></td><td>${n.error_message||'-'}</td></tr>`).join('');
  }).catch(() => {});
}
function toggleAlert(id, enable) {
  fetch(`/api/v1/alerts/${id}/${enable?'enable':'disable'}`, {method:'POST'}).then(() => {
    showToast(enable?'规则已启用':'规则已禁用', enable?'success':'warning');
    loadAlerts();
  }).catch(() => showToast('操作失败','error'));
}
function saveAlertRule() {
  const modal = document.getElementById('modal-alert');
  const inputs = modal.querySelectorAll('.form-input, .form-select');
  const rule = {
    note: inputs[0]?.value || '',
    stockCode: inputs[1]?.value || '',
    alertType: inputs[2]?.value?.split('(')[1]?.replace(')','') || 'price_above',
    thresholdValue: parseFloat(inputs[3]?.value) || 0,
    notifyChannels: 'default',
    enabled: true
  };
  fetch('/api/v1/alerts', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(rule)})
    .then(() => { closeModal('modal-alert'); showToast('告警规则已创建','success'); loadAlerts(); })
    .catch(() => showToast('创建失败','error'));
}

/** 回测调用API */
function runBacktest() {
  const page = document.getElementById('page-backtest');
  const inputs = page.querySelectorAll('.form-input, .form-select');
  const stockCode = inputs[0]?.value?.trim();
  if (!stockCode) { showToast('请输入股票代码', 'warning'); return; }
  const strategy = inputs[1]?.value || 'ma_golden_cross';
  const startDate = inputs[2]?.value || '';
  const endDate = inputs[3]?.value || '';
  const capital = parseFloat((inputs[4]?.value||'100000').replace(/,/g,'')) || 100000;
  showToast('回测任务已提交...', 'info');
  fetch('/api/v1/backtest/run', {
    method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({stock_code: stockCode, strategy, start_date: startDate, end_date: endDate, initial_capital: capital})
  }).then(r => r.json()).then(data => {
    if (data.status === 'failed') { showToast(data.message || '回测失败', 'error'); return; }
    // 更新统计卡片
    const cards = document.querySelectorAll('#page-backtest .stat-card');
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = (data.totalReturnPct!=null?data.totalReturnPct.toFixed(1):'-')+'%';
    if (cards[1]) cards[1].querySelector('.stat-value').textContent = (data.maxDrawdownPct!=null?'-'+data.maxDrawdownPct.toFixed(1):'-')+'%';
    if (cards[2]) cards[2].querySelector('.stat-value').textContent = data.sharpeRatio!=null?data.sharpeRatio.toFixed(2):'-';
    if (cards[3]) cards[3].querySelector('.stat-value').textContent = (data.winRatePct!=null?data.winRatePct.toFixed(1):'-')+'%';
    showToast('回测完成', 'success');
  }).catch(err => showToast('回测失败: '+err.message, 'error'));
}

/** 投资组合加载 */
function loadPortfolio() {
  // 加载概要
  fetch('/api/v1/portfolio/summary').then(r => r.json()).then(s => {
    const cards = document.querySelectorAll('#page-portfolio .stat-card');
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = '¥'+(s.total_market_value||0).toLocaleString();
    if (cards[1]) cards[1].querySelector('.stat-value').textContent = (s.total_profit_loss>=0?'+':'')+'¥'+(s.total_profit_loss||0).toLocaleString();
    if (cards[2]) cards[2].querySelector('.stat-value').textContent = (s.total_return_pct>=0?'+':'')+(s.total_return_pct||0).toFixed(2)+'%';
    if (cards[3]) cards[3].querySelector('.stat-value').textContent = s.total_positions || 0;
  }).catch(() => {});
  // 加载持仓
  fetch('/api/v1/portfolio/positions').then(r => r.json()).then(positions => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="positions"] table tbody');
    if (!tbody) return;
    if (!positions.length) { tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:var(--text-dim);padding:24px">暂无持仓数据，请录入交易或添加持仓</td></tr>'; return; }
    tbody.innerHTML = positions.map(p => {
      const pnlCls = (p.profitLoss||0) >= 0 ? 'text-up' : 'text-down';
      const pctCls = (p.profitLossPct||0) >= 0 ? 'text-up' : 'text-down';
      return `<tr><td><strong>${p.stockName||''}</strong> ${p.stockCode||''}</td><td>${p.quantity||0}</td><td>${(p.costPrice||0).toFixed(2)}</td><td>${(p.currentPrice||0).toFixed(2)}</td><td class="${pnlCls}">${(p.profitLoss>=0?'+':'')+(p.profitLoss||0).toLocaleString()}</td><td class="${pctCls}">${(p.profitLossPct>=0?'+':'')+(p.profitLossPct||0).toFixed(2)}%</td><td>${(p.positionPct||0).toFixed(1)}%</td><td>-</td><td>-</td></tr>`;
    }).join('');
  }).catch(() => {});
  // 加载交易记录
  fetch('/api/v1/portfolio/trades').then(r => r.json()).then(trades => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="trades"] table tbody');
    if (!tbody) return;
    if (!trades.length) { tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无交易记录</td></tr>'; return; }
    tbody.innerHTML = trades.map(t => {
      const sideBadge = t.side === 'buy' ? '<span class="badge badge-success">买入</span>' : '<span class="badge badge-danger">卖出</span>';
      return `<tr><td>${t.tradeDate||'-'}</td><td>${t.symbol||''}</td><td>${sideBadge}</td><td>${t.quantity||0}</td><td>${t.price||0}</td><td>${t.amount||0}</td><td>${t.fee||0}</td><td>${t.note||''}</td></tr>`;
    }).join('');
  }).catch(() => {});
  // 加载资金流水
  fetch('/api/v1/portfolio/cash-ledger').then(r => r.json()).then(entries => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="cashflow"] table tbody');
    if (!tbody) return;
    if (!entries.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-dim);padding:24px">暂无资金流水</td></tr>'; return; }
    tbody.innerHTML = entries.map(e => {
      const dirBadge = e.direction === 'in' ? '<span class="badge badge-success">入金</span>' : '<span class="badge badge-danger">出金</span>';
      const amtCls = e.direction === 'in' ? 'text-up' : 'text-down';
      return `<tr><td>${e.entryDate||'-'}</td><td>${dirBadge}</td><td class="${amtCls}">${e.direction==='in'?'+':''}${e.amount||0}</td><td>${e.currency||'CNY'}</td><td>${e.note||''}</td></tr>`;
    }).join('');
  }).catch(() => {});
  // 加载公司行动
  fetch('/api/v1/portfolio/corporate-actions').then(r => r.json()).then(actions => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="actions"] table tbody');
    if (!tbody) return;
    if (!actions.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-dim);padding:24px">暂无公司行动记录</td></tr>'; return; }
    tbody.innerHTML = actions.map(a => `<tr><td>${a.effectiveDate||'-'}</td><td>${a.symbol||''}</td><td><span class="badge badge-info">${a.actionType||'-'}</span></td><td>${a.description||'-'}</td><td>${a.note||''}</td></tr>`).join('');
  }).catch(() => {});
}
function saveTrade() {
  const modal = document.getElementById('modal-trade');
  const inputs = modal.querySelectorAll('.form-input, .form-select');
  const trade = {
    symbol: inputs[0]?.value||'', side: inputs[1]?.value==='买入'?'buy':'sell',
    quantity: parseInt(inputs[2]?.value)||0, price: parseFloat(inputs[3]?.value)||0,
    tradeDate: inputs[4]?.value||'', fee: parseFloat(inputs[5]?.value)||0,
    note: inputs[7]?.value||''
  };
  trade.amount = trade.quantity * trade.price;
  fetch('/api/v1/portfolio/trades', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(trade)})
    .then(() => { closeModal('modal-trade'); showToast('交易已录入','success'); loadPortfolio(); })
    .catch(() => showToast('录入失败','error'));
}
function openCashEntryForm() {
  const amount = prompt('请输入金额（正数=入金，负数=出金）:');
  if (!amount) return;
  const val = parseFloat(amount);
  if (isNaN(val)) { showToast('请输入有效数字', 'warning'); return; }
  const note = prompt('备注（可选）:') || '';
  const entry = { direction: val >= 0 ? 'in' : 'out', amount: Math.abs(val), currency: 'CNY', note: note };
  fetch('/api/v1/portfolio/cash-ledger', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(entry)})
    .then(() => { showToast('出入金已记录', 'success'); loadPortfolio(); })
    .catch(() => showToast('记录失败', 'error'));
}

/** 市场概况加载 */
function loadMarketOverview() {
  fetch('/api/v1/market/overview').then(r => r.json()).then(data => {
    const light = data.light || {};
    const footer = document.querySelector('.sidebar-footer .sys-status');
    if (footer && light.color) {
      const colors = {green:'绿灯-正常', yellow:'黄灯-谨慎', red:'红灯-高风险', gray:'无数据'};
      footer.innerHTML = `<span class="status-dot" style="background:${light.color==='green'?'var(--success)':light.color==='red'?'var(--danger)':'var(--warning)'}"></span>市场信号灯: ${colors[light.color]||light.color}`;
    }
  }).catch(() => {});
}

// ===== 页面导航时加载数据 =====
const originalNavigateTo = navigateTo;
navigateTo = function(page) {
  originalNavigateTo(page);
  switch(page) {
    case 'dashboard': loadDashboard(); break;
    case 'analysis': loadHistory(); break;
    case 'signals': loadSignals(); break;
    case 'portfolio': loadPortfolio(); break;
    case 'watchlist': loadWatchlist(); break;
    case 'alerts': loadAlerts(); loadAlertTriggers(); loadAlertNotifications(); break;
    case 'usage': loadUsage(); break;
  }
};

// ===== 初始加载 =====
setTimeout(() => {
  loadDashboard();
  loadMarketOverview();
}, 500);
