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
    const days = Array.from({length:90},(_, i)=>{const d=new Date(2023,6,1+i*3);return d.toISOString().slice(0,10);});
    let s=100000,b=100000; const sd=days.map(()=>{s+=(Math.random()-0.38)*2500;return Math.round(s);}); const bd=days.map(()=>{b+=(Math.random()-0.45)*1500;return Math.round(b);});
    chart.setOption({ backgroundColor:'transparent', grid:{top:40,right:20,bottom:30,left:65}, legend:{top:5,textStyle:{fontSize:11}}, xAxis:{type:'category',data:days,axisLabel:{fontSize:10}}, yAxis:{type:'value',axisLabel:{formatter:v=>'¥'+(v/10000).toFixed(1)+'万'}}, series:[{name:'策略净值',data:sd,type:'line',smooth:true,lineStyle:{color:'#6366f1',width:2},itemStyle:{color:'#6366f1'},symbol:'none'},{name:'基准(沪深300)',data:bd,type:'line',smooth:true,lineStyle:{color:'#94a3b8',width:1.5,type:'dashed'},itemStyle:{color:'#94a3b8'},symbol:'none'}], tooltip:{trigger:'axis'} });
  });

  initChart('chart-cost', theme, (chart) => {
    const days = Array.from({length:14},(_, i)=>`03-${String(i+2).padStart(2,'0')}`);
    chart.setOption({ backgroundColor:'transparent', grid:{top:20,right:20,bottom:30,left:50}, xAxis:{type:'category',data:days,axisLabel:{fontSize:10}}, yAxis:{type:'value',axisLabel:{formatter:'¥{value}'}}, series:[{data:days.map(()=>20+Math.random()*40),type:'bar',itemStyle:{color:'#f59e0b',borderRadius:[4,4,0,0]}}], tooltip:{trigger:'axis',formatter:'{b}<br/>费用: ¥{c}'} });
  });

  initChart('chart-model-dist', theme, (chart) => {
    chart.setOption({ backgroundColor:'transparent', series:[{type:'pie',radius:['40%','68%'],center:['50%','55%'],label:{fontSize:11},data:[{value:65,name:'qwen-max',itemStyle:{color:'#6366f1'}},{value:20,name:'gpt-4',itemStyle:{color:'#10b981'}},{value:10,name:'qwen-turbo',itemStyle:{color:'#f59e0b'}},{value:5,name:'ollama',itemStyle:{color:'#94a3b8'}}]}], tooltip:{formatter:'{b}: {c}%'} });
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
