package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 获取用户风险画像工具 — 供 CHAT 工具循环查询当前档位与仓位乘数
 */
@Component
public class GetUserProfileTool implements Tool {

    private final UserRiskProfileService profileService;

    public GetUserProfileTool(UserRiskProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "get_user_profile";
    }

    @Override
    public String description() {
        return "获取用户风险画像：风险承受档位（保守/平衡/激进）、资金规模与建议仓位乘数。"
                + "用户问\"我的风险偏好是什么/我的画像\"或决策建议需要按用户档位调整仓位时调用。";
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
        Map<String, Object> view = profileService.getProfileView();
        return String.format("风险档位: %s（%s） | 资金规模: %s 万元 | 建议仓位乘数: %.1f%s",
                view.get("riskTolerance"), view.get("riskToleranceCn"),
                view.get("capitalAmount") != null ? view.get("capitalAmount") : "未设置",
                (Double) view.get("positionMultiplier"),
                Boolean.TRUE.equals(view.get("defaulted")) ? "（注意：尚未设置，当前为系统默认档）" : "");
    }
}
