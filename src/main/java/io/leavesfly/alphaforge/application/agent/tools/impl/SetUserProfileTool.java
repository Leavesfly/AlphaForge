package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 设置用户风险画像工具 — 对话式登记风险偏好（"我是保守型投资者"→自动设置）
 */
@Component
public class SetUserProfileTool implements Tool {

    private final UserRiskProfileService profileService;

    public SetUserProfileTool(UserRiskProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "set_user_profile";
    }

    @Override
    public String description() {
        return "登记用户风险画像。用户表达风险偏好（如\"我是保守型/平衡型/激进型投资者\"、\"记住我只能接受小幅亏损\"）"
                + "或告知资金规模（如\"我有20万\"）时调用。设置后，决策评分的建议仓位将因人而异。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> riskTolerance = new HashMap<>();
        riskTolerance.put("type", "string");
        riskTolerance.put("description", "风险档位: CONSERVATIVE(保守) / BALANCED(平衡) / AGGRESSIVE(激进)。"
                + "口语映射：保守/稳健/怕亏=CONSERVATIVE；平衡/适中=BALANCED；激进/能承受大波动=AGGRESSIVE");
        properties.put("risk_tolerance", riskTolerance);

        Map<String, Object> capitalAmount = new HashMap<>();
        capitalAmount.put("type", "number");
        capitalAmount.put("description", "总资金规模（万元），用户未提及则不传");
        properties.put("capital_amount", capitalAmount);

        params.put("properties", properties);
        params.put("required", new String[]{"risk_tolerance"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String riskTolerance = (String) args.get("risk_tolerance");
        Double capitalAmount = args.get("capital_amount") != null
                ? Double.parseDouble(String.valueOf(args.get("capital_amount")))
                : null;
        try {
            Map<String, Object> view = profileService.saveProfile(riskTolerance, capitalAmount);
            return String.format("画像已保存：档位=%s（%s），仓位乘数=%.1f。后续决策建议将按此档位调整。",
                    view.get("riskTolerance"), view.get("riskToleranceCn"),
                    (Double) view.get("positionMultiplier"));
        } catch (IllegalArgumentException e) {
            throw new ToolException(e.getMessage(), "PARAM_INVALID");
        }
    }
}
