package io.leavesfly.alphaforge.application.agent.skills;

import java.util.List;
import java.util.Map;

/**
 * alpha-forge-skill CLI {@code --json} 统一输出契约。
 *
 * <p>全部 run_*.py 的 --json 输出为扁平结构：顶层含 schema/command/generated_at 元信息，
 * 以及 summary（1-2 句自然语言结论）、next_steps（结构化链式引导）与业务字段本身。
 * 此处将整个 payload 保留在 {@link #data()} 中，并单独提取 summary/nextSteps 便于转述。</p>
 */
public record SkillResult(
        String schema,
        String summary,
        List<Map<String, Object>> nextSteps,
        Map<String, Object> data,
        String raw) {

    /** 从 payload 中取业务字段（不存在返回 null） */
    public Object get(String key) {
        return data == null ? null : data.get(key);
    }

    /** 业务字段的字符串形式（不存在返回 null） */
    public String str(String key) {
        Object v = get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 将 next_steps 格式化为可读文本，供模型据此做链式引导 */
    public String formatNextSteps() {
        if (nextSteps == null || nextSteps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("建议下一步：\n");
        for (Map<String, Object> step : nextSteps) {
            Object action = step.get("action");
            Object reason = step.get("reason");
            sb.append("  - ").append(action != null ? action : "?")
                    .append("：").append(reason != null ? reason : "").append('\n');
        }
        return sb.toString();
    }
}
