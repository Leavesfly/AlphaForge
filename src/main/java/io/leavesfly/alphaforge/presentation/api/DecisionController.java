package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.agent.kernel.NextStep;
import io.leavesfly.alphaforge.application.agent.kernel.NextStepAdvisor;
import io.leavesfly.alphaforge.application.service.decision.DecisionScoreService;
import io.leavesfly.alphaforge.domain.service.decision.LightsResult;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 买点三灯决策 REST API。
 *
 * <p>仅负责参数绑定，评估编排委托 {@link DecisionScoreService}。</p>
 */
@RestController
@RequestMapping("/api/v1/decision")
public class DecisionController {

    private final DecisionScoreService decisionScoreService;
    private final NextStepAdvisor nextStepAdvisor;

    public DecisionController(DecisionScoreService decisionScoreService, NextStepAdvisor nextStepAdvisor) {
        this.decisionScoreService = decisionScoreService;
        this.nextStepAdvisor = nextStepAdvisor;
    }

    /**
     * 买点三灯评估。
     *
     * @param code 股票代码（如 600519）
     * @param cost 持仓成本价（可选：手动覆盖成本，触发持仓联动视角）
     */
    @GetMapping("/score")
    public ResponseEntity<ApiResponse<Map<String, Object>>> score(
            @RequestParam String code,
            @RequestParam(required = false) Double cost) {
        LightsResult result = decisionScoreService.score(code, cost);
        Map<String, Object> payload = result.toMap();
        // 链式引导：按七态结论附下一步建议（纯新增字段，向后兼容）
        List<Map<String, Object>> nextSteps = nextStepAdvisor
                .adviseForDecision(result.getVerdict().name().toLowerCase(Locale.ROOT), result.getStockCode())
                .stream().map(NextStep::toMap).collect(Collectors.toList());
        payload.put("next_steps", nextSteps);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }
}
