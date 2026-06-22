<#-- 分析报告Markdown模板 -->
<#-- 对应Python版本的 templates/report_markdown.j2 -->
# 📊 ${stockName}(${stockCode}) 分析报告

_分析时间: ${analysisDate}_

## 核心结论

| 指标 | 数值 |
|------|------|
| 交易信号 | **${signal}** |
| 综合评分 | ${totalScore}/100 |
| 置信度 | ${confidence} |
| 当前价格 | ${currentPrice} |
| 涨跌幅 | ${changePct}% |

## 技术面分析

${technicalAnalysis}

## 消息面分析

${newsAnalysis}

## 风险评估

${riskAssessment}

## 操作建议

${operationAdvice}

---
_模型: ${llmModel} | Agent模式: ${agentMode} | 耗时: ${durationSeconds}秒_
