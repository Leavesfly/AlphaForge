package io.leavesfly.alphaforge.application.service.user;

import io.leavesfly.alphaforge.config.UserProfileConfig;
import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import io.leavesfly.alphaforge.domain.repository.user.UserRiskProfileRepository;
import io.leavesfly.alphaforge.domain.service.decision.PositionMultiplier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户风险画像服务 — 读写画像并提供档位/乘数视图。
 *
 * <p>无记录时回退默认档位并标注 defaulted=true（诚实标注，不猜测用户偏好）。</p>
 */
@Service
public class UserRiskProfileService {

    private final UserRiskProfileRepository profileRepository;
    private final UserProfileConfig userProfileConfig;

    public UserRiskProfileService(UserRiskProfileRepository profileRepository,
                                  UserProfileConfig userProfileConfig) {
        this.profileRepository = profileRepository;
        this.userProfileConfig = userProfileConfig;
    }

    /**
     * 获取画像视图：含档位、资金、仓位乘数与是否默认档。
     */
    public Map<String, Object> getProfileView() {
        UserRiskProfile profile = profileRepository.find();
        boolean defaulted = profile == null;
        String tolerance = defaulted
                ? userProfileConfig.getDefaultRiskTolerance()
                : profile.getRiskTolerance();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("riskTolerance", tolerance);
        view.put("riskToleranceCn", toleranceCn(tolerance));
        view.put("capitalAmount", defaulted ? null : profile.getCapitalAmount());
        view.put("positionMultiplier", PositionMultiplier.of(tolerance));
        view.put("defaulted", defaulted);
        return view;
    }

    /**
     * 获取生效仓位乘数（供三灯交易计划使用）。
     */
    public double effectiveMultiplier() {
        UserRiskProfile profile = profileRepository.find();
        String tolerance = profile != null ? profile.getRiskTolerance()
                : userProfileConfig.getDefaultRiskTolerance();
        return PositionMultiplier.of(tolerance);
    }

    /**
     * 保存画像：档位非法抛 IllegalArgumentException（由全局异常处理收敛）。
     */
    public Map<String, Object> saveProfile(String riskTolerance, Double capitalAmount) {
        if (riskTolerance != null) {
            riskTolerance = riskTolerance.trim().toUpperCase();
        }
        if (!UserRiskProfile.isValidTolerance(riskTolerance)) {
            throw new IllegalArgumentException(
                    "风险档位非法: " + riskTolerance + "，可选 CONSERVATIVE/BALANCED/AGGRESSIVE");
        }
        if (capitalAmount != null && (capitalAmount < 0 || capitalAmount > 1_000_000)) {
            throw new IllegalArgumentException("资金规模非法（万元，0 ~ 100万）: " + capitalAmount);
        }

        UserRiskProfile profile = new UserRiskProfile();
        profile.setId(1L);
        profile.setRiskTolerance(riskTolerance);
        profile.setCapitalAmount(capitalAmount);
        profileRepository.upsert(profile);
        return getProfileView();
    }

    private String toleranceCn(String tolerance) {
        return switch (tolerance) {
            case UserRiskProfile.CONSERVATIVE -> "保守";
            case UserRiskProfile.AGGRESSIVE -> "激进";
            default -> "平衡";
        };
    }
}
