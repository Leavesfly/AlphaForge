package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.service.system.DataSourceHealthService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源体检工具 — 供 CHAT 工具循环响应"数据源是不是挂了"。
 *
 * <p>转述守则：必须说明环境问题（限流/无 Key/网络）与代码问题的区别；
 * 必须说明"港美股部分源同上游 Yahoo，独立上游数少于可用源数"，
 * 避免用户把同上游多源误读为多重冗余。</p>
 */
@Component
public class CheckDataSourcesTool implements Tool {

    private static final Map<String, String> STATUS_CN = Map.of(
            "ok", "正常",
            "rate_limit", "被限流",
            "no_key", "缺 Key/Token",
            "network", "网络不通",
            "data_error", "数据异常",
            "unsupported", "无探针标的");

    private final DataSourceHealthService dataSourceHealthService;

    public CheckDataSourcesTool(DataSourceHealthService dataSourceHealthService) {
        this.dataSourceHealthService = dataSourceHealthService;
    }

    @Override
    public String name() {
        return "check_datasources";
    }

    @Override
    public String description() {
        return "对全部行情数据源做主动体检（逐源真实拉取探测，报告可用性/耗时/末根K线日期/错误分类）。"
                + "用户问\"数据源是不是挂了/为什么拉不到行情/数据源状态\"时调用。"
                + "转述时必须区分：限流/无Key/网络属环境问题（换源或补配置即可），数据异常才可能是代码问题；"
                + "并说明港美股部分源同上游 Yahoo，独立上游数少于可用源数。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        params.put("properties", new HashMap<>());
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        Map<String, Object> report = dataSourceHealthService.getCachedOrProbe(false);
        StringBuilder sb = new StringBuilder();

        Object summaryObj = report.get("summary");
        if (summaryObj instanceof Map<?, ?> summary) {
            sb.append(String.format("数据源体检（%s，缓存=%s）：%s 个源中 %s 个可用，%s 个失败（环境问题 %s 个）%n",
                    report.get("checkedAt"), report.get("cached"),
                    summary.get("total"), summary.get("ok"), summary.get("failed"),
                    summary.get("environmentIssues")));
        }
        if (report.get("fetchers") instanceof List<?> fetchers) {
            for (Object f : fetchers) {
                if (!(f instanceof Map<?, ?> item)) continue;
                String status = String.valueOf(item.get("status"));
                sb.append(String.format("- %s（优先级 %s）：%s", item.get("name"), item.get("priority"),
                        STATUS_CN.getOrDefault(status, status)));
                if ("ok".equals(status)) {
                    sb.append(String.format("，耗时 %sms，末根K线 %s（探针 %s）%n",
                            item.get("latencyMs"), item.get("lastBarDate"), item.get("probeSymbol")));
                } else {
                    sb.append(String.format("：%s%n", item.get("error") != null ? item.get("error") : ""));
                }
            }
        }
        sb.append("说明：限流/无Key/网络不通属环境问题（换源或补配置即可恢复），数据异常才可能是代码问题；")
                .append("港美股部分源同上游 Yahoo，独立上游数少于可用源数。");
        return sb.toString();
    }
}
