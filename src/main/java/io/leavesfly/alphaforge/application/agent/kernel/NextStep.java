package io.leavesfly.alphaforge.application.agent.kernel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链式引导建议 — "答完之后，下一步做什么"。
 *
 * <p>{@code action} 为机器码（如 paper_trading_track）；{@code endpoint} 为前端 hash 路由
 * 路径（如 mine/paper-trading），可空表示无跳转；建议是可选引导而非指令。</p>
 */
public record NextStep(String action, String label, String endpoint, String reason) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", action);
        map.put("label", label);
        map.put("endpoint", endpoint);
        map.put("reason", reason);
        return map;
    }
}
