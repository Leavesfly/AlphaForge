<#-- 企业微信报告模板 -->
<#-- 对应Python版本的 templates/report_wechat.j2 -->
## ${stockName}(${stockCode}) 分析报告

> **交易信号**: <font color="${(changePct?number >= 0)?then('warning','comment')}">${signal}</font>
> **综合评分**: ${totalScore}/100
> **当前价格**: ${currentPrice} <font color="${(changePct?number >= 0)?then('warning','comment')}">${changePct}%</font>

### 核心结论

${summary!"暂无"}

### 技术面

${technicalAnalysis!"暂无技术面数据"}

### 操作建议

${operationAdvice!"请结合自身情况判断"}

---
_${analysisDate} | ${llmModel} | ${durationSeconds}秒_
