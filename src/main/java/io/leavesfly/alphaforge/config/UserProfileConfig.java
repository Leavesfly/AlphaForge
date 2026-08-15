package io.leavesfly.alphaforge.config;

import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 用户画像配置 — 默认风险档位与仓位乘数。
 *
 * <p>环境变量：USER_DEFAULT_RISK_TOLERANCE（CONSERVATIVE/BALANCED/AGGRESSIVE，默认 BALANCED）。</p>
 */
@Configuration
public class UserProfileConfig {

    private static final Logger log = LoggerFactory.getLogger(UserProfileConfig.class);

    private final EnvVarProvider envVarProvider;

    /** 未设置画像时的默认风险档位 */
    private String defaultRiskTolerance = UserRiskProfile.BALANCED;

    public UserProfileConfig(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider;
    }

    @PostConstruct
    public void init() {
        String value = envVarProvider.get("USER_DEFAULT_RISK_TOLERANCE", UserRiskProfile.BALANCED).trim().toUpperCase();
        if (UserRiskProfile.isValidTolerance(value)) {
            this.defaultRiskTolerance = value;
        } else {
            log.warn("USER_DEFAULT_RISK_TOLERANCE={} 非法（可选 CONSERVATIVE/BALANCED/AGGRESSIVE），回退 BALANCED", value);
        }
        log.info("用户画像配置加载完成: 默认档位={}", defaultRiskTolerance);
    }

    public String getDefaultRiskTolerance() {
        return defaultRiskTolerance;
    }
}
