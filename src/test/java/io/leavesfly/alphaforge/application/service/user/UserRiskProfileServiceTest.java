package io.leavesfly.alphaforge.application.service.user;

import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.config.UserProfileConfig;
import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import io.leavesfly.alphaforge.domain.repository.user.UserRiskProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRiskProfileService 画像服务测试")
class UserRiskProfileServiceTest {

    @Mock
    private UserRiskProfileRepository repository;

    private UserProfileConfig config;
    private UserRiskProfileService service;

    @BeforeEach
    void setUp() {
        EnvVarProvider envVarProvider = new EnvVarProvider();
        envVarProvider.init();
        config = new UserProfileConfig(envVarProvider);
        config.init();
        service = new UserRiskProfileService(repository, config);
    }

    @Test
    @DisplayName("无记录时回退默认档并标注 defaulted=true")
    void noRecordReturnsDefaultWithFlag() {
        when(repository.find()).thenReturn(null);

        Map<String, Object> view = service.getProfileView();

        assertEquals(UserRiskProfile.BALANCED, view.get("riskTolerance"));
        assertEquals(1.0, (Double) view.get("positionMultiplier"), 1e-9);
        assertEquals(Boolean.TRUE, view.get("defaulted"));
    }

    @Test
    @DisplayName("已设置画像时返回真实档位与乘数")
    void savedProfileReturnsActualTolerance() {
        UserRiskProfile profile = new UserRiskProfile();
        profile.setRiskTolerance(UserRiskProfile.AGGRESSIVE);
        profile.setCapitalAmount(20.0);
        when(repository.find()).thenReturn(profile);

        Map<String, Object> view = service.getProfileView();

        assertEquals(UserRiskProfile.AGGRESSIVE, view.get("riskTolerance"));
        assertEquals("激进", view.get("riskToleranceCn"));
        assertEquals(1.5, (Double) view.get("positionMultiplier"), 1e-9);
        assertEquals(Boolean.FALSE, view.get("defaulted"));
    }

    @Test
    @DisplayName("保存画像：合法档位（大小写不敏感）应触发 upsert")
    void saveValidProfileUpserts() {
        // mock 仓库不存状态：upsert 后的 getProfileView 走 find()，stub 为已保存画像模拟读回一致
        UserRiskProfile saved = new UserRiskProfile();
        saved.setRiskTolerance(UserRiskProfile.CONSERVATIVE);
        saved.setCapitalAmount(10.0);
        when(repository.find()).thenReturn(saved);

        Map<String, Object> view = service.saveProfile("conservative", 10.0);

        assertEquals(UserRiskProfile.CONSERVATIVE, view.get("riskTolerance"));
        verify(repository).upsert(any(UserRiskProfile.class));
    }

    @Test
    @DisplayName("保存画像：非法档位应抛 IllegalArgumentException")
    void saveInvalidToleranceThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.saveProfile("SUPER_SAFE", null));
    }

    @Test
    @DisplayName("保存画像：负资金应抛 IllegalArgumentException")
    void saveNegativeCapitalThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.saveProfile("BALANCED", -1.0));
    }

    @Test
    @DisplayName("effectiveMultiplier 无画像时取默认档乘数")
    void effectiveMultiplierWithoutProfile() {
        when(repository.find()).thenReturn(null);
        assertTrue(service.effectiveMultiplier() > 0);
    }
}
