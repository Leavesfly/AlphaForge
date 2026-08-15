package io.leavesfly.alphaforge.domain.service.decision;

import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PositionMultiplier 风险档位乘数映射测试")
class PositionMultiplierTest {

    @Test
    @DisplayName("三档标准映射")
    void standardTolerances() {
        assertEquals(0.5, PositionMultiplier.of(UserRiskProfile.CONSERVATIVE));
        assertEquals(1.0, PositionMultiplier.of(UserRiskProfile.BALANCED));
        assertEquals(1.5, PositionMultiplier.of(UserRiskProfile.AGGRESSIVE));
    }

    @Test
    @DisplayName("未知或空档位按保守处理（宁可少给仓位）")
    void unknownOrNullFallsBackToConservative() {
        assertEquals(0.5, PositionMultiplier.of(null));
        assertEquals(0.5, PositionMultiplier.of(""));
        assertEquals(0.5, PositionMultiplier.of("WHATEVER"));
    }
}
