// ===== Charts =====
let chartsInitialized = {};

function getChartTheme() {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : null;
}

function initCharts() {
  const theme = getChartTheme();
  
  initChart('chart-accuracy', theme, (chart) => {
    const days = Array.from({length:30}, (_, i) => `${String(Math.floor(i/30*3)+1).padStart(2,'0')}-${String(i%28+1).padStart(2,'0')}`);
    chart.setOption({ backgroundColor:'transparent', grid:{top:30,right:20,bottom:30,left:50}, xAxis:{type:'category',data:days,axisLabel:{fontSize:10}}, yAxis:{type:'value',min:40,max:100,axisLabel:{formatter:'{value}%'}}, series:[{data:days.map(()=>55+Math.random()*30),type:'line',smooth:true,areaStyle:{opacity:0.12,color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'rgba(99,102,241,0.4)'},{offset:1,color:'rgba(99,102,241,0)'}]}},lineStyle:{color:'#6366f1',width:2},itemStyle:{color:'#6366f1'},symbol:'none'}], tooltip:{trigger:'axis',formatter:'{b}<br/>准确率: {c}%'} });
  });

  initChart('chart-tokens', theme, (chart) => {
    const days = Array.from({length:14}, (_, i) => `03-${String(i+2).padStart(2,'0')}`);
    chart.setOption({ backgroundColor:'transparent', grid:{top:35,right:20,bottom:30,left:60}, xAxis:{type:'category',data:days,axisLabel:{fontSize:10}}, yAxis:{type:'value',axisLabel:{formatter:v=>(v/1000)+'K'}}, series:[{name:'输入',data:days.map(()=>400000+Math.random()*300000),type:'bar',stack:'t',itemStyle:{color:'#6366f1'}},{name:'输出',data:days.map(()=>150000+Math.random()*200000),type:'bar',stack:'t',itemStyle:{color:'#a78bfa',borderRadius:[3,3,0,0]}}], legend:{top:0,textStyle:{fontSize:11}}, tooltip:{trigger:'axis'} });
  });

  initChart('chart-portfolio', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', series:[{type:'pie',radius:['45%','72%'],center:['50%','55%'],label:{fontSize:11},data:[{value:172000,name:'贵州茅台',itemStyle:{color:'#6366f1'}},{value:107650,name:'宁德时代',itemStyle:{color:'#10b981'}},{value:94880,name:'中际旭创',itemStyle:{color:'#f59e0b'}},{value:75680,name:'腾讯控股',itemStyle:{color:'#3b82f6'}},{value:80550,name:'比亚迪',itemStyle:{color:'#ef4444'}},{value:35600,name:'招商银行',itemStyle:{color:'#8b5cf6'}}]}], tooltip:{formatter:'{b}: ¥{c} ({d}%)'} });
  });

  initChart('chart-pnl', theme, (chart) => {
    const days = Array.from({length:60},(_, i)=>{const d=new Date(2024,0,15+i);return `${d.getMonth()+1}-${String(d.getDate()).padStart(2,'0')}`;});
    let v=1000000; const vals=days.map(()=>{v+=(Math.random()-0.42)*15000;return Math.round(v);});
    chart.setOption({ backgroundColor:'transparent', grid:{top:20,right:20,bottom:30,left:65}, xAxis:{type:'category',data:days,axisLabel:{fontSize:10}}, yAxis:{type:'value',axisLabel:{formatter:v=>'¥'+(v/10000).toFixed(0)+'万'}}, series:[{data:vals,type:'line',smooth:true,lineStyle:{color:'#10b981',width:2},areaStyle:{color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'rgba(16,185,129,0.25)'},{offset:1,color:'rgba(16,185,129,0)'}]}},itemStyle:{color:'#10b981'},symbol:'none'}], tooltip:{trigger:'axis'} });
  });

  initChart('chart-backtest', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', title:{text:'暂无回测数据',left:'center',top:'middle',textStyle:{color:'#94a3b8',fontSize:13,fontWeight:'normal'}}, xAxis:{type:'category',data:[]}, yAxis:{type:'value'}, series:[] });
  });

  initChart('chart-backtest-drawdown', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', grid:{top:20,right:20,bottom:30,left:50}, xAxis:{type:'category',data:[]}, yAxis:{type:'value',axisLabel:{formatter:'{value}%'}}, series:[{type:'line',data:[],areaStyle:{opacity:0.2,color:'#ef4444'},lineStyle:{color:'#ef4444',width:1.5},symbol:'none'}] });
  });

  initChart('chart-backtest-monthly', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', grid:{top:20,right:20,bottom:30,left:50}, xAxis:{type:'category',data:[]}, yAxis:{type:'value',axisLabel:{formatter:'{value}%'}}, series:[{type:'bar',data:[],itemStyle:{color: params => (params.data >= 0 ? '#10b981' : '#ef4444'), borderRadius:[4,4,0,0]}}], tooltip:{trigger:'axis'} });
  });

  initChart('chart-cost', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', grid:{top:20,right:20,bottom:30,left:50}, xAxis:{type:'category',data:[],axisLabel:{fontSize:10}}, yAxis:{type:'value',axisLabel:{formatter:'¥{value}'}}, series:[{data:[],type:'bar',itemStyle:{color:'#f59e0b',borderRadius:[4,4,0,0]}}], tooltip:{trigger:'axis',formatter:'{b}<br/>费用: ¥{c}'} });
  });

  initChart('chart-model-dist', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', series:[{type:'pie',radius:['40%','68%'],center:['50%','55%'],label:{fontSize:11},data:[{value:1,name:'暂无数据',itemStyle:{color:'#e5e7eb'}}]}], tooltip:{formatter:'{b}: {c} tokens ({d}%)'} });
  });
}

function initChart(id, theme, setup) {
  const el = document.getElementById(id);
  if (!el || el.offsetParent === null) return;
  if (el._inited && !el._needsReinit) return;
  // Dispose old instance if exists
  const existing = echarts.getInstanceByDom(el);
  if (existing) existing.dispose();
  el._inited = true;
  el._needsReinit = false;
  const chart = echarts.init(el, theme);
  setup(chart);
  new ResizeObserver(() => chart.resize()).observe(el);
}

// Init on load
setTimeout(initCharts, 300);

function renderBacktestVisualization(data) {
  if (!data) return;
  ['chart-backtest', 'chart-backtest-drawdown', 'chart-backtest-monthly'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el._needsReinit = true;
  });
  const theme = getChartTheme();
  const curve = data.equity_curve || {};
  const dates = curve.dates || [];
  const portfolio = curve.portfolio_values || [];
  const benchmark = curve.benchmark_values || [];
  const drawdown = curve.drawdown_pct || [];
  const closePrices = curve.close_prices || [];
  const markers = data.trade_markers || [];

  const buyPoints = markers.filter(m => m.side === 'buy').map(m => [m.date, m.price]);
  const sellPoints = markers.filter(m => m.side === 'sell').map(m => [m.date, m.price]);

  initChart('chart-backtest', theme, (chart) => {
    chart.setOption({
      backgroundColor: 'transparent',
      title: dates.length ? undefined : { text: '暂无回测数据', left: 'center', top: 'middle', textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' } },
      grid: { top: 55, right: 55, bottom: 30, left: 65 },
      legend: { top: 5, textStyle: { fontSize: 11 } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10 } },
      yAxis: [
        { type: 'value', name: '净值', axisLabel: { formatter: v => '¥' + (v / 10000).toFixed(1) + '万' } },
        { type: 'value', name: '股价', show: closePrices.length > 0, axisLabel: { formatter: v => v.toFixed(0) }, splitLine: { show: false } }
      ],
      series: [
        { name: '策略净值', type: 'line', data: portfolio, smooth: true, yAxisIndex: 0, lineStyle: { color: '#6366f1', width: 2 }, itemStyle: { color: '#6366f1' }, symbol: 'none' },
        { name: '买入持有', type: 'line', data: benchmark, smooth: true, yAxisIndex: 0, lineStyle: { color: '#94a3b8', width: 1.5, type: 'dashed' }, itemStyle: { color: '#94a3b8' }, symbol: 'none' },
        { name: '股价', type: 'line', data: closePrices, smooth: true, yAxisIndex: 1, lineStyle: { color: '#f59e0b', width: 1, opacity: 0.5 }, itemStyle: { color: '#f59e0b' }, symbol: 'none' },
        { name: '买入', type: 'scatter', yAxisIndex: 1, data: buyPoints, symbol: 'triangle', symbolSize: 12, itemStyle: { color: '#10b981' } },
        { name: '卖出', type: 'scatter', yAxisIndex: 1, data: sellPoints, symbol: 'triangle', symbolRotate: 180, symbolSize: 12, itemStyle: { color: '#ef4444' } }
      ]
    }, true);
  });

  initChart('chart-backtest-drawdown', theme, (chart) => {
    chart.setOption({
      backgroundColor: 'transparent', grid: { top: 20, right: 20, bottom: 30, left: 50 },
      tooltip: { trigger: 'axis', formatter: p => `${p[0].axisValue}<br/>回撤: -${Number(p[0].data).toFixed(2)}%` },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', axisLabel: { formatter: '-{value}%' } },
      series: [{ type: 'line', data: drawdown.map(v => -Math.abs(v)), areaStyle: { opacity: 0.18, color: '#ef4444' }, lineStyle: { color: '#ef4444', width: 1.5 }, symbol: 'none' }]
    }, true);
  });

  const monthly = data.monthly_returns || [];
  initChart('chart-backtest-monthly', theme, (chart) => {
    chart.setOption({
      backgroundColor: 'transparent', grid: { top: 20, right: 20, bottom: 30, left: 50 },
      tooltip: { trigger: 'axis', formatter: p => `${p[0].axisValue}<br/>收益: ${Number(p[0].data).toFixed(2)}%` },
      xAxis: { type: 'category', data: monthly.map(m => m.month), axisLabel: { fontSize: 10, rotate: monthly.length > 8 ? 35 : 0 } },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
      series: [{ type: 'bar', data: monthly.map(m => Number(m.return_pct || 0).toFixed(2)),
        itemStyle: { color: params => (params.data >= 0 ? '#10b981' : '#ef4444'), borderRadius: [4, 4, 0, 0] } }]
    }, true);
  });

  const summary = data.summary || {};
  const setText = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
  setText('bt-total-return', summary.total_return_pct != null ? Number(summary.total_return_pct).toFixed(2) + '%' : '-');
  setText('bt-max-drawdown', summary.max_drawdown_pct != null ? '-' + Number(summary.max_drawdown_pct).toFixed(2) + '%' : '-');
  setText('bt-sharpe', summary.sharpe_ratio != null ? Number(summary.sharpe_ratio).toFixed(2) : '-');
  setText('bt-win-rate', summary.win_rate_pct != null ? Number(summary.win_rate_pct).toFixed(1) + '%' : '-');

  const tbody = document.getElementById('backtest-trades-body');
  const trades = data.trades || [];
  if (tbody) {
    if (!trades.length) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-dim);padding:24px">本次回测无成交记录</td></tr>';
    } else {
      tbody.innerHTML = trades.map(t => {
        const side = t.side === 'buy' ? '<span class="badge badge-success">买入</span>' : '<span class="badge badge-danger">卖出</span>';
        return `<tr><td>${t.date || '-'}</td><td>${side}</td><td>${Number(t.price || 0).toFixed(2)}</td><td>${t.shares || 0}</td><td>${Number(t.amount || 0).toFixed(0)}</td><td>${Number(t.commission || 0).toFixed(2)}</td><td>${Number(t.stamp_tax || 0).toFixed(2)}</td><td>${t.reason || '-'}</td></tr>`;
      }).join('');
    }
  }
}
