package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.autonomy.AutonomyPolicy;
import io.leavesfly.alphaforge.application.autonomy.TradingRiskGuard;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L4 自主控制 API — 状态查询、熔断/恢复、运行时开关。
 */
@RestController
@RequestMapping("/api/v1/autonomy")
public class AutonomyController {

    private final AutonomyPolicy policy;
    private final TradingRiskGuard riskGuard;
    private final AutonomyConfig config;

    public AutonomyController(AutonomyPolicy policy, TradingRiskGuard riskGuard, AutonomyConfig config) {
        this.policy = policy;
        this.riskGuard = riskGuard;
        this.config = config;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", config.isEnabled());
        m.put("auto_execute_signals", config.isAutoExecuteSignals());
        m.put("auto_promote", config.isAutoPromote());
        m.put("auto_demote", config.isAutoDemote());
        m.put("auto_apply_params", config.isAutoApplyParams());
        m.put("min_promote_grade", config.getMinPromoteGrade());
        m.put("paper_account_id", config.getPaperAccountId());
        m.put("halted", riskGuard.isHalted());
        m.put("halt_reason", riskGuard.getHaltReason());
        m.put("recent_audit", policy.getAuditLog().recent(20));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping("/halt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> halt(@RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : "manual";
        riskGuard.halt(reason);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("halted", true, "reason", reason)));
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resume() {
        riskGuard.resume();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("halted", false)));
    }

    /**
     * 运行时绑定纸面账户（进程内生效，重启后需重新绑定或设环境变量）。
     * POST /api/v1/autonomy/bind-paper-account
     * body: { "account_id": 1 } 或 { "account_id": null } 清除
     */
    @PostMapping("/bind-paper-account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindPaperAccount(
            @RequestBody(required = false) Map<String, Object> body) {
        Long accountId = null;
        if (body != null && body.get("account_id") != null
                && !String.valueOf(body.get("account_id")).isBlank()
                && !"null".equalsIgnoreCase(String.valueOf(body.get("account_id")))) {
            try {
                accountId = Long.parseLong(String.valueOf(body.get("account_id")).trim());
            } catch (NumberFormatException e) {
                return ResponseEntity.ok(ApiResponse.error("account_id 无效"));
            }
        }
        config.setPaperAccountIdRuntime(accountId);
        policy.audit("bind_paper_account", "account",
                accountId != null ? String.valueOf(accountId) : "none",
                "runtime", "bound",
                accountId != null ? "bound to " + accountId : "cleared");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paper_account_id", accountId);
        m.put("note", "运行时绑定，进程重启后失效；持久化请设 AUTONOMY_PAPER_ACCOUNT_ID");
        return ResponseEntity.ok(ApiResponse.ok(m, "纸面账户已绑定"));
    }
}
