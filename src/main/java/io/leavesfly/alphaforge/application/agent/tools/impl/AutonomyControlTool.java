package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.autonomy.TradingRiskGuard;
import io.leavesfly.alphaforge.application.service.portfolio.PaperTradingService;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioAccount;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * L4 自主控制工具 — 供 Chat/ReAct 用自然语言查询与操作纸面闭环。
 *
 * 支持：查状态、开关自主策略、绑定纸面账户、熔断/恢复、查审计。
 * 运行时修改仅影响当前进程；持久化仍依赖环境变量。
 */
@Component
public class AutonomyControlTool implements Tool {

    private final AutonomyConfig config;
    private final AutonomyPolicy policy;
    private final TradingRiskGuard riskGuard;
    private final PaperTradingService paperTradingService;

    public AutonomyControlTool(AutonomyConfig config,
                               AutonomyPolicy policy,
                               TradingRiskGuard riskGuard,
                               PaperTradingService paperTradingService) {
        this.config = config;
        this.policy = policy;
        this.riskGuard = riskGuard;
        this.paperTradingService = paperTradingService;
    }

    @Override
    public String name() {
        return "autonomy_control";
    }

    @Override
    public String description() {
        return "L4 纸面自主控制：查询自主状态与审计、开启/关闭自主与自动执行/晋升/降级/写参、"
                + "绑定或清除纸面账户、紧急熔断与恢复交易。"
                + "当用户说「打开自主」「绑定模拟账户」「查审计」「熔断」「恢复交易」时必须调用本工具。"
                + "注意：开关为运行时生效，进程重启后恢复为环境变量配置。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> action = new HashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型: status | set_flags | bind_account | clear_account | "
                + "list_accounts | halt | resume | audit");
        action.put("enum", List.of(
                "status", "set_flags", "bind_account", "clear_account",
                "list_accounts", "halt", "resume", "audit"));
        properties.put("action", action);

        Map<String, Object> enabled = new HashMap<>();
        enabled.put("type", "boolean");
        enabled.put("description", "set_flags: 是否开启 AUTONOMY_ENABLED");
        properties.put("enabled", enabled);

        Map<String, Object> autoExecute = new HashMap<>();
        autoExecute.put("type", "boolean");
        autoExecute.put("description", "set_flags: 是否自动执行信号");
        properties.put("auto_execute_signals", autoExecute);

        Map<String, Object> autoPromote = new HashMap<>();
        autoPromote.put("type", "boolean");
        autoPromote.put("description", "set_flags: 是否自动晋升策略");
        properties.put("auto_promote", autoPromote);

        Map<String, Object> autoDemote = new HashMap<>();
        autoDemote.put("type", "boolean");
        autoDemote.put("description", "set_flags: 是否自动降级差策略");
        properties.put("auto_demote", autoDemote);

        Map<String, Object> autoApply = new HashMap<>();
        autoApply.put("type", "boolean");
        autoApply.put("description", "set_flags: 是否自动写回优化参数");
        properties.put("auto_apply_params", autoApply);

        Map<String, Object> accountId = new HashMap<>();
        accountId.put("type", "integer");
        accountId.put("description", "bind_account: 纸面账户 ID");
        properties.put("account_id", accountId);

        Map<String, Object> accountName = new HashMap<>();
        accountName.put("type", "string");
        accountName.put("description", "bind_account: 按账户名称模糊匹配（无 account_id 时使用）");
        properties.put("account_name", accountName);

        Map<String, Object> reason = new HashMap<>();
        reason.put("type", "string");
        reason.put("description", "halt: 熔断原因");
        properties.put("reason", reason);

        Map<String, Object> limit = new HashMap<>();
        limit.put("type", "integer");
        limit.put("description", "audit: 返回最近 N 条，默认 10");
        properties.put("limit", limit);

        params.put("properties", properties);
        params.put("required", new String[]{"action"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String action = str(args.get("action"));
        if (action.isBlank()) {
            throw new ToolException("参数 action 不能为空", "PARAM_MISSING");
        }

        return switch (action.toLowerCase()) {
            case "status" -> formatStatus();
            case "set_flags" -> setFlags(args);
            case "bind_account" -> bindAccount(args);
            case "clear_account" -> clearAccount();
            case "list_accounts" -> listAccounts();
            case "halt" -> halt(args);
            case "resume" -> resume();
            case "audit" -> formatAudit(args);
            default -> throw new ToolException("不支持的 action: " + action, "PARAM_INVALID");
        };
    }

    private String formatStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("L4 自主状态（运行时）:\n");
        sb.append(String.format("- enabled: %s\n", config.isEnabled()));
        sb.append(String.format("- auto_execute_signals: %s\n", config.isAutoExecuteSignals()));
        sb.append(String.format("- auto_promote: %s\n", config.isAutoPromote()));
        sb.append(String.format("- auto_demote: %s\n", config.isAutoDemote()));
        sb.append(String.format("- auto_apply_params: %s\n", config.isAutoApplyParams()));
        sb.append(String.format("- min_promote_grade: %s\n", config.getMinPromoteGrade()));
        sb.append(String.format("- paper_account_id: %s\n",
                config.getPaperAccountId() != null ? config.getPaperAccountId() : "未绑定"));
        sb.append(String.format("- halted: %s\n", riskGuard.isHalted()));
        if (riskGuard.isHalted()) {
            sb.append(String.format("- halt_reason: %s\n", riskGuard.getHaltReason()));
        }
        sb.append("说明: 以上为当前进程运行时值；重启后恢复环境变量配置。");
        return sb.toString();
    }

    private String setFlags(Map<String, Object> args) {
        StringBuilder changed = new StringBuilder();
        boolean touchChildOn = false;

        if (args.containsKey("enabled")) {
            boolean v = bool(args.get("enabled"));
            config.setEnabledRuntime(v);
            changed.append("enabled=").append(v).append("; ");
        }
        if (args.containsKey("auto_execute_signals")) {
            boolean v = bool(args.get("auto_execute_signals"));
            config.setAutoExecuteSignalsRuntime(v);
            changed.append("auto_execute_signals=").append(v).append("; ");
            if (v) touchChildOn = true;
        }
        if (args.containsKey("auto_promote")) {
            boolean v = bool(args.get("auto_promote"));
            config.setAutoPromoteRuntime(v);
            changed.append("auto_promote=").append(v).append("; ");
            if (v) touchChildOn = true;
        }
        if (args.containsKey("auto_demote")) {
            boolean v = bool(args.get("auto_demote"));
            config.setAutoDemoteRuntime(v);
            changed.append("auto_demote=").append(v).append("; ");
            if (v) touchChildOn = true;
        }
        if (args.containsKey("auto_apply_params")) {
            boolean v = bool(args.get("auto_apply_params"));
            config.setAutoApplyParamsRuntime(v);
            changed.append("auto_apply_params=").append(v).append("; ");
            if (v) touchChildOn = true;
        }
        // 子开关打开时自动打开总开关，否则 effective 仍为 false
        if (touchChildOn && !config.isEnabled() && !args.containsKey("enabled")) {
            config.setEnabledRuntime(true);
            changed.append("enabled=true(auto); ");
        }
        if (changed.isEmpty()) {
            return "未提供任何可修改标志。可传 enabled / auto_execute_signals / auto_promote / auto_demote / auto_apply_params。\n"
                    + formatStatus();
        }
        policy.audit("chat_set_flags", "autonomy", "runtime",
                "chat", "updated", changed.toString());
        return "已更新运行时开关: " + changed + "\n" + formatStatus();
    }

    private String bindAccount(Map<String, Object> args) throws ToolException {
        Long resolvedId = null;
        if (args.get("account_id") instanceof Number n) {
            resolvedId = n.longValue();
        } else if (args.get("account_id") != null && !str(args.get("account_id")).isBlank()) {
            try {
                resolvedId = Long.parseLong(str(args.get("account_id")));
            } catch (NumberFormatException e) {
                throw new ToolException("account_id 无效", "PARAM_INVALID");
            }
        }

        String name = str(args.get("account_name"));
        if (resolvedId == null && !name.isBlank()) {
            List<PortfolioAccount> matches = paperTradingService.getPaperAccounts().stream()
                    .filter(a -> a.getName() != null && a.getName().contains(name))
                    .toList();
            if (matches.isEmpty()) {
                throw new ToolException("未找到名称包含「" + name + "」的模拟账户，请先 list_accounts", "NOT_FOUND");
            }
            if (matches.size() > 1) {
                String opts = matches.stream()
                        .map(a -> "#" + a.getId() + " " + a.getName())
                        .collect(Collectors.joining(", "));
                throw new ToolException("匹配到多个账户，请用 account_id 指定: " + opts, "AMBIGUOUS");
            }
            resolvedId = matches.get(0).getId();
        }

        if (resolvedId == null) {
            throw new ToolException("请提供 account_id 或 account_name", "PARAM_MISSING");
        }

        final Long accountId = resolvedId;
        PortfolioAccount account = paperTradingService.getPaperAccounts().stream()
                .filter(a -> accountId.equals(a.getId()))
                .findFirst()
                .orElse(null);
        if (account == null) {
            throw new ToolException("账户不存在: " + accountId, "NOT_FOUND");
        }

        config.setPaperAccountIdRuntime(accountId);
        policy.audit("chat_bind_account", "account", String.valueOf(accountId),
                "chat", "bound", "name=" + account.getName());
        return String.format("已绑定纸面账户 #%d（%s）。自动执行信号将使用该账户。\n%s",
                accountId, account.getName(), formatStatus());
    }

    private String clearAccount() {
        config.setPaperAccountIdRuntime(null);
        policy.audit("chat_clear_account", "account", "none", "chat", "cleared", "cleared via chat");
        return "已清除纸面账户绑定。\n" + formatStatus();
    }

    private String listAccounts() {
        List<PortfolioAccount> accounts = paperTradingService.getPaperAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return "暂无模拟账户。请先在「模拟交易」页面创建，或让用户创建后再绑定。";
        }
        StringBuilder sb = new StringBuilder("可用模拟账户:\n");
        for (PortfolioAccount a : accounts) {
            sb.append(String.format("- #%d %s | 现金=%.2f | 市场=%s\n",
                    a.getId(), a.getName(),
                    a.getCashBalance() != null ? a.getCashBalance() : 0.0,
                    a.getMarket() != null ? a.getMarket() : "-"));
        }
        Long bound = config.getPaperAccountId();
        sb.append("当前绑定: ").append(bound != null ? "#" + bound : "未绑定");
        return sb.toString();
    }

    private String halt(Map<String, Object> args) {
        String reason = str(args.get("reason"));
        if (reason.isBlank()) reason = "chat halt";
        riskGuard.halt(reason);
        return "已紧急熔断纸面交易。原因: " + reason + "\n" + formatStatus();
    }

    private String resume() {
        riskGuard.resume();
        return "已恢复纸面交易。\n" + formatStatus();
    }

    private String formatAudit(Map<String, Object> args) {
        int limit = 10;
        if (args.get("limit") instanceof Number n) {
            limit = Math.max(1, Math.min(50, n.intValue()));
        }
        List<Map<String, Object>> entries = policy.getAuditLog().recent(limit);
        if (entries.isEmpty()) {
            return "暂无自主动作审计。开启自主并产生晋升/执行/熔断等动作后会出现记录。";
        }
        StringBuilder sb = new StringBuilder("最近自主审计（最多 " + limit + " 条，新→旧）:\n");
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map<String, Object> a = entries.get(i);
            sb.append(String.format("- [%s] %s %s:%s %s→%s | %s\n",
                    a.getOrDefault("ts", "-"),
                    a.getOrDefault("action", "-"),
                    a.getOrDefault("entity_type", "-"),
                    a.getOrDefault("entity_id", "-"),
                    a.getOrDefault("from_state", "-"),
                    a.getOrDefault("to_state", "-"),
                    a.getOrDefault("reason", "-")));
        }
        return sb.toString().trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        String s = str(o).toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s) || "开".equals(s);
    }
}
