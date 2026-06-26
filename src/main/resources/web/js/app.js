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
  // 同步 URL hash，刷新后保持当前页面
  if (location.hash !== '#' + page) {
    location.hash = page;
  }
  setTimeout(initCharts, 150);
  if (page === 'chat') initChat();
}
document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', () => navigateTo(item.dataset.page));
});

// 监听浏览器前进/后退，同步页面切换
window.addEventListener('hashchange', () => {
  const hashPage = location.hash.replace('#', '');
  const activePage = document.querySelector('.page.active');
  const activePageName = activePage ? activePage.id.replace('page-', '') : '';
  if (hashPage && hashPage !== activePageName) {
    navigateTo(hashPage);
  }
});

// 页面加载时从 URL hash 恢复当前页面（刷新保持）
// 用 setTimeout 延迟执行，确保所有 let/const 变量（如 chatInitialized）已完成初始化
setTimeout(() => {
  const initialHash = location.hash.replace('#', '');
  if (initialHash && document.getElementById('page-' + initialHash)) {
    navigateTo(initialHash);
  }
}, 0);

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
let currentSessionId = null;
let chatInitialized = false;

chatInput.addEventListener('input', function() { this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 120) + 'px'; });
chatInput.addEventListener('keydown', function(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChatMessage(); } });

/** 初始化聊天页面 - 加载会话列表 */
function initChat() {
  if (chatInitialized) return;
  chatInitialized = true;
  loadSessionList();
}

/** 从后端加载会话列表 */
function loadSessionList() {
  fetch('/api/v1/agent/sessions')
    .then(r => r.json())
    .then(sessions => {
      const list = document.getElementById('chat-session-list');
      list.innerHTML = '';
      if (!sessions.length) {
        createNewSession();
        return;
      }
      sessions.forEach(s => {
        const item = document.createElement('div');
        item.className = 'chat-session-item';
        item.dataset.sessionId = s.sessionId;
        item.innerHTML = `<div class="session-title">${escapeHtml(s.title)}</div><div class="session-meta"><span>${s.messageCount}条消息</span><span>${formatSessionTime(s.lastActive)}</span></div><button class="session-delete-btn" title="删除会话">&times;</button>`;
        item.addEventListener('click', () => selectSession(s.sessionId));
        item.querySelector('.session-delete-btn').addEventListener('click', (e) => {
          e.stopPropagation();
          deleteSession(s.sessionId);
        });
        list.appendChild(item);
      });
      // 自动选中最近的会话
      selectSession(sessions[0].sessionId);
    })
    .catch(err => {
      showToast('加载会话列表失败: ' + err.message, 'error');
    });
}

/** 选择会话并加载消息 */
function selectSession(sessionId) {
  currentSessionId = sessionId;
  document.querySelectorAll('.chat-session-item').forEach(s => s.classList.remove('active'));
  const item = document.querySelector(`.chat-session-item[data-session-id="${sessionId}"]`);
  if (item) item.classList.add('active');
  loadSessionMessages(sessionId);
}

/** 从后端加载会话消息 */
function loadSessionMessages(sessionId) {
  fetch(`/api/v1/agent/sessions/${sessionId}/messages`)
    .then(r => r.json())
    .then(messages => {
      chatMessages.innerHTML = '';
      chatHistory = [];
      if (!messages.length) {
        appendMessage('assistant', '你好！我是你的 AI 股票分析助手。我可以帮你：\n• 分析个股（技术面/基本面/舆情）\n• 解读行情与市场动向\n• 运行策略回测\n• 设置价格告警\n\n直接输入问题即可开始，比如"分析一下贵州茅台"。');
        return;
      }
      messages.forEach(msg => {
        appendMessage(msg.role, msg.content, msg.createdAt);
        chatHistory.push({role: msg.role, content: msg.content});
      });
    })
    .catch(err => {
      showToast('加载消息失败: ' + err.message, 'error');
    });
}

/** 新建会话 */
function newChatSession() {
  createNewSession();
}

function createNewSession() {
  fetch('/api/v1/agent/sessions', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({title: '新对话'})
  })
    .then(r => r.json())
    .then(session => {
      currentSessionId = session.sessionId;
      const list = document.getElementById('chat-session-list');
      const item = document.createElement('div');
      item.className = 'chat-session-item active';
      item.dataset.sessionId = session.sessionId;
      item.innerHTML = `<div class="session-title">${escapeHtml(session.title)}</div><div class="session-meta"><span>0条消息</span><span>刚刚</span></div>`;
      item.addEventListener('click', () => selectSession(session.sessionId));
      list.querySelectorAll('.chat-session-item').forEach(s => s.classList.remove('active'));
      list.prepend(item);
      chatMessages.innerHTML = '';
      chatHistory = [];
      appendMessage('assistant', '你好！新对话已创建。有什么我可以帮你分析的吗？');
    })
    .catch(err => {
      showToast('创建会话失败: ' + err.message, 'error');
    });
}

function sendChatMessage() {
  const text = chatInput.value.trim();
  if (!text) return;
  if (!currentSessionId) {
    showToast('请先选择或创建一个会话', 'warning');
    return;
  }
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
  const contentWrapper = bubble.parentElement;

  // 调用流式API
  fetch('/api/v1/agent/chat/stream', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({message: text, sessionId: currentSessionId, history: chatHistory.slice(-10)})
  }).then(response => {
    bubble.innerHTML = '';
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let fullText = '';
    let buffer = '';

    function processSSEEvents() {
      const events = buffer.split('\n\n');
      buffer = events.pop(); // 保留最后不完整的部分
      for (const evt of events) {
        let eventName = 'message';
        let eventData = '';
        for (const line of evt.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            eventData = line.substring(5).trim();
          }
        }
        if (eventName === 'tool') {
          try {
            const toolInfo = JSON.parse(eventData);
            appendToolCall(contentWrapper, bubble, toolInfo);
          } catch(e) { /* ignore */ }
        } else if (eventName === 'chunk') {
          fullText += eventData;
          bubble.innerHTML = renderMarkdown(fullText);
          chatMessages.scrollTop = chatMessages.scrollHeight;
        } else if (eventName === 'done') {
          // 流式完成
        } else if (eventName === 'error') {
          bubble.innerHTML = '<span style="color:var(--danger)">错误: ' + escapeHtml(eventData) + '</span>';
        }
      }
    }

    function read() {
      reader.read().then(({done, value}) => {
        if (done) {
          // 最终渲染 Markdown
          bubble.innerHTML = renderMarkdown(fullText);
          bubble.classList.add('md-content');
          chatHistory.push({role: 'assistant', content: fullText});
          msgEl.querySelector('.chat-time').textContent = new Date().toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'});
          refreshSessionItem(currentSessionId);
          return;
        }
        buffer += decoder.decode(value, {stream: true});
        processSSEEvents();
        chatMessages.scrollTop = chatMessages.scrollHeight;
        read();
      });
    }
    read();
  }).catch(err => {
    bubble.innerHTML = '抱歉，AI服务暂时不可用: ' + escapeHtml(err.message);
  });
}

/** 发送完成后刷新会话列表项的消息数和标题 */
function refreshSessionItem(sessionId) {
  fetch('/api/v1/agent/sessions')
    .then(r => r.json())
    .then(sessions => {
      const s = sessions.find(x => x.sessionId === sessionId);
      if (!s) return;
      const item = document.querySelector(`.chat-session-item[data-session-id="${sessionId}"]`);
      if (!item) return;
      item.querySelector('.session-title').textContent = escapeHtml(s.title);
      item.querySelector('.session-meta').innerHTML = `<span>${s.messageCount}条消息</span><span>${formatSessionTime(s.lastActive)}</span>`;
    })
    .catch(() => {});
}

/** 格式化会话时间显示 */
function formatSessionTime(timeStr) {
  if (!timeStr) return '';
  try {
    const date = new Date(timeStr);
    const now = new Date();
    const diff = now - date;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
    if (date.toDateString() === now.toDateString())
      return date.toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'});
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) return '昨天';
    return date.toLocaleDateString('zh-CN', {month:'2-digit', day:'2-digit'});
  } catch(e) { return timeStr; }
}

/** HTML转义 */
function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Markdown渲染 */
function renderMarkdown(text) {
  if (!text) return '';
  if (typeof marked !== 'undefined' && marked.parse) {
    try { return marked.parse(text); }
    catch(e) { return escapeHtml(text).replace(/\n/g, '<br>'); }
  }
  return escapeHtml(text).replace(/\n/g, '<br>');
}

/** 删除会话 */
function deleteSession(sessionId) {
  if (!confirm('确定删除这个会话吗？')) return;
  fetch(`/api/v1/agent/sessions/${sessionId}`, { method: 'DELETE' })
    .then(r => r.json())
    .then(() => {
      const item = document.querySelector(`.chat-session-item[data-session-id="${sessionId}"]`);
      if (item) item.remove();
      if (currentSessionId === sessionId) {
        const firstItem = document.querySelector('.chat-session-item');
        if (firstItem) {
          selectSession(firstItem.dataset.sessionId);
        } else {
          createNewSession();
        }
      }
      showToast('会话已删除', 'success');
    })
    .catch(err => { showToast('删除失败: ' + err.message, 'error'); });
}

/** 在消息区域插入工具调用卡片（折叠式） */
function appendToolCall(contentWrapper, bubble, toolInfo) {
  const toolEl = document.createElement('div');
  toolEl.className = 'chat-tool-call';
  const argsStr = toolInfo.args ? Object.entries(toolInfo.args).map(([k,v]) => `${k}=${v}`).join(', ') : '';
  const duration = toolInfo.durationMs != null
    ? (toolInfo.durationMs < 1000 ? toolInfo.durationMs + 'ms' : (toolInfo.durationMs / 1000).toFixed(1) + 's')
    : '';
  toolEl.innerHTML = `
    <div class="tool-call-header">
      <span class="tool-call-icon">🔧</span>
      <span class="tool-call-name">${escapeHtml(toolInfo.name)}</span>
      ${duration ? `<span class="tool-call-duration">${duration}</span>` : ''}
      <span class="tool-call-arrow">▸</span>
    </div>
    <div class="tool-call-body">
      ${argsStr ? `<div class="tool-call-section"><div class="tool-call-label">入参</div><div class="tool-call-value">${escapeHtml(argsStr)}</div></div>` : ''}
      <div class="tool-call-section"><div class="tool-call-label">返回结果</div><div class="tool-call-value">${escapeHtml(toolInfo.result)}</div></div>
    </div>
  `;
  toolEl.querySelector('.tool-call-header').addEventListener('click', () => {
    toolEl.classList.toggle('expanded');
    chatMessages.scrollTop = chatMessages.scrollHeight;
  });
  contentWrapper.insertBefore(toolEl, bubble);
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

function appendMessage(role, text, timeStr) {
  let time;
  if (timeStr) {
    try { time = new Date(timeStr).toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'}); }
    catch(e) { time = new Date().toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'}); }
  } else {
    time = new Date().toLocaleTimeString('zh-CN', {hour:'2-digit', minute:'2-digit'});
  }
  const avatar = role === 'user' ? 'Y' : 'AI';
  const msg = document.createElement('div');
  msg.className = `chat-msg ${role}`;
  const bubbleContent = role === 'assistant' ? renderMarkdown(text) : escapeHtml(text).replace(/\n/g, '<br>');
  const bubbleClass = role === 'assistant' ? 'chat-bubble md-content' : 'chat-bubble';
  msg.innerHTML = `<div class="chat-avatar">${avatar}</div><div><div class="${bubbleClass}">${bubbleContent}</div><div class="chat-time">${time}</div></div>`;
  chatMessages.appendChild(msg);
  chatMessages.scrollTop = chatMessages.scrollHeight;
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
    const today = data.today || {};
    const monthly = data.monthly_total || {};

    // 更新统计卡片
    const cards = document.querySelectorAll('#page-usage .stat-card');
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = today.calls || 0;
    if (cards[1]) cards[1].querySelector('.stat-value').textContent = formatTokenCount(today.total_tokens);
    if (cards[2]) cards[2].querySelector('.stat-value').textContent = formatCostStr(today.estimated_cost);
    if (cards[3]) cards[3].querySelector('.stat-value').textContent = '¥' + Number(monthly.total_cost || 0).toFixed(2);

    // 渲染每日明细表格
    const tbody = document.querySelector('#page-usage table tbody');
    if (tbody) {
      const detail = data.daily_detail || [];
      if (!detail.length) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无用量数据，进行一次 AI 分析后将自动记录</td></tr>';
      } else {
        tbody.innerHTML = detail.map(d =>
          '<tr><td>' + (d.date||'-') + '</td><td>' + (d.model||'-') + '</td><td>' + (d.provider||'-') +
          '</td><td>' + (d.calls||0) + '</td><td>' + formatNumber(d.prompt_tokens) +
          '</td><td>' + formatNumber(d.completion_tokens) + '</td><td>' + formatNumber(d.total_tokens) +
          '</td><td>¥' + Number(d.cost||0).toFixed(2) + '</td></tr>'
        ).join('');
      }
    }

    // 渲染费用趋势图
    renderCostTrendChart(data.cost_trend || []);
    // 渲染模型占比图
    renderModelDistChart(data.model_distribution || []);
  }).catch(() => {});
}

function formatTokenCount(n) {
  n = Number(n) || 0;
  if (n >= 1000000) return (n/1000000).toFixed(1)+'M';
  if (n >= 1000) return (n/1000).toFixed(0)+'K';
  return String(n);
}

function formatCostStr(c) {
  if (!c) return '¥0.00';
  c = String(c);
  if (c.startsWith('¥') || c.startsWith('$')) return c;
  return '¥' + Number(c).toFixed(2);
}

function formatNumber(n) {
  return Number(n || 0).toLocaleString();
}

function renderCostTrendChart(trend) {
  const el = document.getElementById('chart-cost');
  if (!el) return;
  let chart = echarts.getInstanceByDom(el);
  if (!chart) chart = echarts.init(el, getChartTheme());

  const sorted = [...trend].reverse(); // 日期从旧到新
  const dates = sorted.map(t => t.date ? String(t.date).substring(5) : '-');
  const costs = sorted.map(t => Number(t.cost || 0).toFixed(2));
  const tokens = sorted.map(t => Number(t.total_tokens || 0));

  chart.setOption({
    backgroundColor: 'transparent',
    grid: {top: 35, right: 50, bottom: 30, left: 55},
    legend: {top: 0, textStyle: {fontSize: 11}},
    xAxis: {type: 'category', data: dates, axisLabel: {fontSize: 10}},
    yAxis: [
      {type: 'value', name: '费用(¥)', position: 'left', axisLabel: {formatter: '¥{value}'}},
      {type: 'value', name: 'Token', position: 'right', axisLabel: {formatter: v => v >= 1000 ? (v/1000).toFixed(0)+'K' : v}}
    ],
    series: [
      {name: '费用(¥)', data: costs, type: 'bar', itemStyle: {color: '#f59e0b', borderRadius: [4,4,0,0]}},
      {name: 'Token', data: tokens, type: 'line', yAxisIndex: 1, smooth: true, lineStyle: {color: '#6366f1', width: 2}, itemStyle: {color: '#6366f1'}, symbol: 'circle', symbolSize: 5}
    ],
    tooltip: {trigger: 'axis'}
  }, true);
}

function renderModelDistChart(dist) {
  const el = document.getElementById('chart-model-dist');
  if (!el) return;
  let chart = echarts.getInstanceByDom(el);
  if (!chart) chart = echarts.init(el, getChartTheme());

  const colors = ['#6366f1', '#10b981', '#f59e0b', '#3b82f6', '#ef4444', '#8b5cf6', '#94a3b8'];
  const pieData = dist.map((d, i) => ({
    value: Number(d.total_tokens || 0),
    name: d.model || 'unknown',
    itemStyle: {color: colors[i % colors.length]}
  }));

  chart.setOption({
    backgroundColor: 'transparent',
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['50%', '55%'],
      label: {fontSize: 11, formatter: '{b}\n{d}%'},
      data: pieData.length > 0 ? pieData : [{value: 1, name: '暂无数据', itemStyle: {color: '#e5e7eb'}}]
    }],
    tooltip: {formatter: '{b}: {c} tokens ({d}%)'}
  }, true);
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

// ===== Portfolio =====
function loadPortfolio() {
  fetch('/api/v1/portfolio/summary').then(r => r.json()).then(data => {
    const cards = document.querySelectorAll('#page-portfolio .stat-card');
    if (cards[0]) cards[0].querySelector('.stat-value').textContent = '¥' + Number(data.total_market_value || 0).toLocaleString();
    if (cards[1]) { const pnl = Number(data.total_profit_loss || 0); cards[1].querySelector('.stat-value').textContent = (pnl>=0?'+':'') + '¥' + Math.abs(pnl).toLocaleString(); cards[1].querySelector('.stat-value').className = 'stat-value ' + (pnl>=0?'text-up':'text-down'); }
    if (cards[2]) cards[2].querySelector('.stat-value').textContent = Number(data.total_return_pct || 0).toFixed(2) + '%';
    if (cards[3]) cards[3].querySelector('.stat-value').textContent = data.total_positions || 0;
  }).catch(() => {});
  fetch('/api/v1/portfolio/positions').then(r => r.json()).then(positions => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="positions"] table tbody');
    if (!tbody) return;
    if (!positions.length) { tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:var(--text-dim);padding:24px">暂无持仓数据</td></tr>'; return; }
    tbody.innerHTML = positions.map(p => {
      const cost = Number(p.costPrice||p.cost_price||0), cur = Number(p.currentPrice||p.current_price||0), qty = Number(p.quantity||0);
      const pnl = (cur - cost) * qty, pnlPct = cost ? ((cur-cost)/cost*100).toFixed(2) : '0.00';
      const cls = pnl >= 0 ? 'text-up' : 'text-down';
      return `<tr><td><strong>${p.stockName||p.stock_name||''}</strong> ${p.stockCode||p.stock_code||''}</td><td>${qty}</td><td>${cost.toFixed(2)}</td><td>${cur.toFixed(2)}</td><td class="${cls}">${pnl>=0?'+':''}${pnl.toFixed(0)}</td><td class="${cls}">${pnlPct>=0?'+':''}${pnlPct}%</td><td>${p.weight?(p.weight*100).toFixed(1):'-'}%</td><td>${p.stopLoss||p.stop_loss||'-'}</td><td>${p.targetPrice||p.target_price||'-'}</td></tr>`;
    }).join('');
  }).catch(() => {});
  fetch('/api/v1/portfolio/trades').then(r => r.json()).then(trades => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="trades"] table tbody');
    if (!tbody) return;
    if (!trades.length) { tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无交易记录</td></tr>'; return; }
    tbody.innerHTML = trades.map(t => `<tr><td>${t.tradeDate||t.trade_date||'-'}</td><td>${t.stockName||t.stock_name||''} ${t.stockCode||t.stock_code||''}</td><td>${t.side==='buy'?'<span class="badge badge-success">买入</span>':'<span class="badge badge-danger">卖出</span>'}</td><td>${t.quantity||0}</td><td>${t.price||'-'}</td><td>${t.totalAmount||t.total_amount||'-'}</td><td>${t.fee||'-'}</td><td>${t.note||'-'}</td></tr>`).join('');
  }).catch(() => {});
  fetch('/api/v1/portfolio/cash-ledger').then(r => r.json()).then(entries => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="cashflow"] table tbody');
    if (!tbody) return;
    if (!entries.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-dim);padding:24px">暂无资金流水</td></tr>'; return; }
    tbody.innerHTML = entries.map(e => `<tr><td>${e.entryDate||e.entry_date||'-'}</td><td>${e.direction==='in'?'<span class="badge badge-success">入金</span>':'<span class="badge badge-danger">出金</span>'}</td><td class="${e.direction==='in'?'text-up':'text-down'}">${e.direction==='in'?'+':'-'}${e.amount||0}</td><td>${e.currency||'CNY'}</td><td>${e.note||'-'}</td></tr>`).join('');
  }).catch(() => {});
  fetch('/api/v1/portfolio/corporate-actions').then(r => r.json()).then(actions => {
    const tbody = document.querySelector('#page-portfolio .tab-panel[data-panel="actions"] table tbody');
    if (!tbody) return;
    if (!actions.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-dim);padding:24px">暂无公司行动</td></tr>'; return; }
    tbody.innerHTML = actions.map(a => `<tr><td>${a.effectiveDate||a.effective_date||'-'}</td><td>${a.stockName||a.stock_name||''} ${a.stockCode||a.stock_code||''}</td><td><span class="badge badge-info">${a.actionType||a.action_type||'-'}</span></td><td>${a.details||'-'}</td><td>${a.note||'-'}</td></tr>`).join('');
  }).catch(() => {});
}

// ===== Backtest =====
function initBacktestPage() {
  const start = document.getElementById('backtest-start');
  const end = document.getElementById('backtest-end');
  if (start && !start.value) {
    const d = new Date();
    d.setMonth(d.getMonth() - 12);
    start.value = d.toISOString().slice(0, 10);
  }
  if (end && !end.value) {
    end.value = new Date().toISOString().slice(0, 10);
  }
  loadBacktestStrategies();
  loadBacktestHistory();
}

function loadBacktestStrategies() {
  fetch('/api/v1/strategies?capability=backtest').then(r => r.json()).then(resp => {
    const list = resp.strategies || resp;
    const select = document.getElementById('backtest-strategy');
    if (!select || !Array.isArray(list) || !list.length) return;
    select.innerHTML = list
      .filter(s => s.available !== false)
      .map(s => `<option value="${s.id}">${s.label || s.id}</option>`)
      .join('');
  }).catch(() => {});
}

function viewBacktestVisualization(recordId) {
  if (!recordId) return;
  fetch(`/api/v1/backtest/${recordId}/visualization`).then(r => {
    if (!r.ok) throw new Error('not found');
    return r.json();
  }).then(data => {
    renderBacktestVisualization(data);
    showToast('已加载回测可视化', 'success');
  }).catch(() => showToast('加载可视化数据失败', 'error'));
}

function loadBacktestHistory() {
  fetch('/api/v1/backtest/history').then(r => r.json()).then(records => {
    const tbody = document.getElementById('backtest-history-body');
    if (!tbody) return;
    if (!records || !records.length) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无回测记录，请在上方运行回测</td></tr>';
      return;
    }
    tbody.innerHTML = records.map(r => {
      const id = r.id;
      const created = (r.createdAt || r.created_at || '-').toString().replace('T', ' ').slice(0, 16);
      const code = r.stockCode || r.stock_code || '';
      const name = r.stockName || r.stock_name || '';
      const strategy = r.strategyName || r.strategy_name || '-';
      const ret = r.totalReturnPct ?? r.total_return_pct;
      const dd = r.maxDrawdownPct ?? r.max_drawdown_pct;
      const win = r.winRatePct ?? r.win_rate_pct;
      const sharpe = r.sharpeRatio ?? r.sharpe_ratio;
      return `<tr>
        <td>${created}</td>
        <td><strong>${name}</strong> ${code}</td>
        <td><span class="badge badge-info">${strategy}</span></td>
        <td class="${Number(ret) >= 0 ? 'text-up' : 'text-down'}">${ret != null ? Number(ret).toFixed(2) + '%' : '-'}</td>
        <td>${dd != null ? '-' + Number(dd).toFixed(2) + '%' : '-'}</td>
        <td>${win != null ? Number(win).toFixed(1) + '%' : '-'}</td>
        <td>${sharpe != null ? Number(sharpe).toFixed(2) : '-'}</td>
        <td><button class="btn btn-sm btn-outline" onclick="viewBacktestVisualization(${id})">查看图表</button></td>
      </tr>`;
    }).join('');
    if (records[0]?.id) {
      viewBacktestVisualization(records[0].id);
    }
  }).catch(() => {});
}

function runBacktest() {
  const code = document.getElementById('backtest-code')?.value?.trim();
  if (!code) { showToast('请输入股票代码', 'warning'); return; }
  const strategy = document.getElementById('backtest-strategy')?.value || 'ma_golden_cross';
  const startDate = document.getElementById('backtest-start')?.value;
  const endDate = document.getElementById('backtest-end')?.value;
  const capital = parseFloat((document.getElementById('backtest-capital')?.value || '100000').replace(/,/g, ''));
  showToast('回测运行中...', 'info');
  fetch('/api/v1/backtest/run', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stock_code: code, strategy, start_date: startDate, end_date: endDate, initial_capital: capital })
  }).then(r => r.json()).then(data => {
    if (data.status === 'failed') { showToast(data.message || '回测失败', 'error'); return; }
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast('回测完成', 'success');
    loadBacktestHistory();
    if (data.id) {
      viewBacktestVisualization(data.id);
    }
  }).catch(() => showToast('回测请求失败', 'error'));
}

// ===== Paper Trading =====
let currentPaperAccountId = null;

function loadPaperTrading() {
  fetch('/api/v1/paper-trading/accounts').then(r => r.json()).then(accounts => {
    const select = document.getElementById('paper-account-select');
    if (!select) return;
    if (!accounts.length) {
      select.innerHTML = '<option value="">-- 暂无模拟账户，请创建 --</option>';
      resetPaperUI();
      return;
    }
    select.innerHTML = '<option value="">-- 请选择账户 --</option>' +
      accounts.map(a => `<option value="${a.id}">${a.name} (资金¥${Number(a.cashBalance||0).toLocaleString()})</option>`).join('');
    // 自动选择第一个账户
    if (currentPaperAccountId && accounts.some(a => a.id == currentPaperAccountId)) {
      select.value = currentPaperAccountId;
      renderPaperAccountDetail(currentPaperAccountId);
    }
  }).catch(() => {
    const select = document.getElementById('paper-account-select');
    if (select) select.innerHTML = '<option value="">-- 加载失败 --</option>';
  });
}

function onPaperAccountChange() {
  const select = document.getElementById('paper-account-select');
  if (!select) return;
  const accountId = select.value;
  currentPaperAccountId = accountId || null;
  if (accountId) {
    renderPaperAccountDetail(accountId);
  } else {
    resetPaperUI();
  }
}

function resetPaperUI() {
  document.getElementById('paper-net-assets').textContent = '¥0';
  document.getElementById('paper-cash').textContent = '¥0';
  document.getElementById('paper-positions-value').textContent = '¥0';
  document.getElementById('paper-pnl').textContent = '¥0';
  document.getElementById('paper-pnl').className = 'stat-value';
  document.getElementById('paper-loan-balance').textContent = '¥0';
  document.getElementById('paper-loan-limit').textContent = '¥0';
  document.getElementById('paper-available-loan').textContent = '¥0';
  document.getElementById('paper-positions-body').innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">请先选择模拟账户</td></tr>';
  document.getElementById('paper-trades-body').innerHTML = '<tr><td colspan="9" style="text-align:center;color:var(--text-dim);padding:24px">请先选择模拟账户</td></tr>';
}

function renderPaperAccountDetail(accountId) {
  fetch(`/api/v1/paper-trading/accounts/${accountId}`).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    const cash = Number(data.cashBalance || 0);
    const posVal = Number(data.positionsValue || 0);
    const netAssets = Number(data.netAssets || 0);
    const pnl = Number(data.totalProfitLoss || 0);
    const loanBal = Number(data.loanBalance || 0);
    const loanLim = Number(data.loanLimit || 0);
    const availLoan = Number(data.availableLoan || 0);
    document.getElementById('paper-net-assets').textContent = '¥' + netAssets.toLocaleString();
    document.getElementById('paper-cash').textContent = '¥' + cash.toLocaleString();
    document.getElementById('paper-positions-value').textContent = '¥' + posVal.toLocaleString();
    document.getElementById('paper-loan-balance').textContent = '¥' + loanBal.toLocaleString();
    document.getElementById('paper-loan-limit').textContent = '¥' + loanLim.toLocaleString();
    document.getElementById('paper-available-loan').textContent = '¥' + availLoan.toLocaleString();
    const pnlEl = document.getElementById('paper-pnl');
    pnlEl.textContent = (pnl >= 0 ? '+' : '') + '¥' + Math.abs(pnl).toLocaleString();
    pnlEl.className = 'stat-value ' + (pnl >= 0 ? 'text-up' : 'text-down');
    // 渲染持仓
    const posBody = document.getElementById('paper-positions-body');
    const positions = data.positions || [];
    if (!positions.length) {
      posBody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">暂无持仓</td></tr>';
    } else {
      posBody.innerHTML = positions.map(p => {
        const pnlVal = Number(p.profitLoss || 0);
        const pnlPct = Number(p.profitLossPct || 0);
        const cls = pnlVal >= 0 ? 'text-up' : 'text-down';
        return `<tr><td><strong>${p.stockName || p.stockCode}</strong> ${p.stockCode}</td><td>${p.quantity || 0}</td><td>${Number(p.costPrice || 0).toFixed(2)}</td><td>${Number(p.currentPrice || 0).toFixed(2)}</td><td>¥${Number(p.marketValue || 0).toLocaleString()}</td><td class="${cls}">${pnlVal >= 0 ? '+' : ''}${pnlVal.toFixed(0)}</td><td class="${cls}">${pnlPct >= 0 ? '+' : ''}${pnlPct.toFixed(2)}%</td><td>${Number(p.positionPct || 0).toFixed(1)}%</td></tr>`;
      }).join('');
    }
  }).catch(() => showToast('加载账户详情失败', 'error'));
  // 加载交易记录
  fetch(`/api/v1/paper-trading/accounts/${accountId}/trades`).then(r => r.json()).then(trades => {
    const tradeBody = document.getElementById('paper-trades-body');
    if (!trades || !trades.length) {
      tradeBody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:var(--text-dim);padding:24px">暂无交易记录</td></tr>';
      return;
    }
    tradeBody.innerHTML = trades.map(t => {
      const sideBadge = t.side === 'buy' ? '<span class="badge badge-success">买入</span>' : '<span class="badge badge-danger">卖出</span>';
      const amount = Number(t.quantity || 0) * Number(t.price || 0);
      return `<tr><td>${t.tradeDate || '-'}</td><td>${t.note || t.symbol || ''}</td><td>${sideBadge}</td><td>${t.quantity || 0}</td><td>${Number(t.price || 0).toFixed(2)}</td><td>¥${amount.toLocaleString()}</td><td>${Number(t.fee || 0).toFixed(2)}</td><td>${Number(t.tax || 0).toFixed(2)}</td><td>${t.note || ''}</td></tr>`;
    }).join('');
  }).catch(() => {});
}

function createPaperAccount() {
  const name = document.getElementById('paper-account-name')?.value?.trim();
  if (!name) { showToast('请输入账户名称', 'warning'); return; }
  const market = document.getElementById('paper-account-market')?.value || 'cn';
  const capital = parseFloat(document.getElementById('paper-account-capital')?.value || '100000');
  fetch('/api/v1/paper-trading/accounts', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ name, market, initialCapital: capital })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    closeModal('modal-paper-account');
    showToast('模拟账户创建成功', 'success');
    currentPaperAccountId = data.id;
    loadPaperTrading();
  }).catch(() => showToast('创建失败', 'error'));
}

function paperBuy() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  const stockCode = document.getElementById('paper-order-code')?.value?.trim();
  if (!stockCode) { showToast('请输入股票代码', 'warning'); return; }
  const quantity = parseInt(document.getElementById('paper-order-qty')?.value || '0');
  if (!quantity || quantity <= 0) { showToast('请输入有效数量', 'warning'); return; }
  showToast('买入下单中...', 'info');
  fetch(`/api/v1/paper-trading/accounts/${accountId}/buy`, {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ stockCode, quantity })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast(`买入成功: ${data.stockName} ${data.quantity}股 @${Number(data.price).toFixed(2)} 手续费${Number(data.fee).toFixed(2)}`, 'success');
    renderPaperAccountDetail(accountId);
    loadPaperTrading();
  }).catch(() => showToast('买入失败', 'error'));
}

function paperSell() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  const stockCode = document.getElementById('paper-order-code')?.value?.trim();
  if (!stockCode) { showToast('请输入股票代码', 'warning'); return; }
  const quantity = parseInt(document.getElementById('paper-order-qty')?.value || '0');
  if (!quantity || quantity <= 0) { showToast('请输入有效数量', 'warning'); return; }
  showToast('卖出下单中...', 'info');
  fetch(`/api/v1/paper-trading/accounts/${accountId}/sell`, {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ stockCode, quantity })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast(`卖出成功: ${data.stockName} ${data.quantity}股 @${Number(data.price).toFixed(2)} 手续费${Number(data.fee).toFixed(2)} 税${Number(data.tax).toFixed(2)}`, 'success');
    renderPaperAccountDetail(accountId);
    loadPaperTrading();
  }).catch(() => showToast('卖出失败', 'error'));
}

function refreshPaperPositions() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  showToast('刷新行情中...', 'info');
  fetch(`/api/v1/paper-trading/accounts/${accountId}/positions/refresh`, { method: 'POST' })
    .then(r => r.json())
    .then(data => {
      if (data.error) { showToast(data.error, 'error'); return; }
      showToast('持仓行情已刷新', 'success');
      renderPaperAccountDetail(accountId);
    }).catch(() => showToast('刷新失败', 'error'));
}

function resetPaperAccount() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  if (!confirm('确认重置该模拟账户？所有持仓和交易记录将被清空，资金将重置。')) return;
  const capital = parseFloat(prompt('请输入重置后的资金金额:', '100000') || '100000');
  fetch(`/api/v1/paper-trading/accounts/${accountId}/reset`, {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ capital })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast('账户已重置', 'success');
    renderPaperAccountDetail(accountId);
    loadPaperTrading();
  }).catch(() => showToast('重置失败', 'error'));
}

function paperBorrow() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  const amount = parseFloat(document.getElementById('paper-loan-amount')?.value || '0');
  if (!amount || amount <= 0) { showToast('请输入借款金额', 'warning'); return; }
  fetch(`/api/v1/paper-trading/accounts/${accountId}/borrow`, {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ amount })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast(`借款成功: ¥${data.amount} 当前负债¥${data.loanBalance}`, 'success');
    renderPaperAccountDetail(accountId);
    loadPaperTrading();
  }).catch(() => showToast('借款失败', 'error'));
}

function paperRepay() {
  const accountId = currentPaperAccountId;
  if (!accountId) { showToast('请先选择模拟账户', 'warning'); return; }
  const amount = parseFloat(document.getElementById('paper-loan-amount')?.value || '0');
  if (!amount || amount <= 0) { showToast('请输入归还金额', 'warning'); return; }
  fetch(`/api/v1/paper-trading/accounts/${accountId}/repay`, {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ amount })
  }).then(r => r.json()).then(data => {
    if (data.error) { showToast(data.error, 'error'); return; }
    showToast(`归还成功: ¥${data.amount} 剩余负债¥${data.loanBalance}`, 'success');
    renderPaperAccountDetail(accountId);
    loadPaperTrading();
  }).catch(() => showToast('归还失败', 'error'));
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
    case 'paper-trading': loadPaperTrading(); break;
    case 'watchlist': loadWatchlist(); break;
    case 'alerts': loadAlerts(); loadAlertTriggers(); loadAlertNotifications(); break;
    case 'usage': loadUsage(); break;
    case 'backtest': initBacktestPage(); break;
  }
};

// ===== 初始加载 =====
setTimeout(() => {
  loadDashboard();
  loadMarketOverview();
}, 500);
