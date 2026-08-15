package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import io.leavesfly.alphaforge.domain.model.entity.user.UserRiskProfile;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController 画像 API 测试")
class ProfileControllerTest {

    @Mock
    private UserRiskProfileService profileService;

    private ProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProfileController(profileService);
    }

    @Test
    @DisplayName("GET /profile 返回画像视图")
    void getProfileReturnsView() {
        when(profileService.getProfileView()).thenReturn(Map.of(
                "riskTolerance", UserRiskProfile.BALANCED,
                "defaulted", true));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.getProfile();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        assertEquals(UserRiskProfile.BALANCED, response.getBody().getData().get("riskTolerance"));
    }

    @Test
    @DisplayName("PUT /profile 委托保存并返回视图")
    void putProfileSavesAndReturnsView() {
        Map<String, Object> saved = Map.of("riskTolerance", UserRiskProfile.CONSERVATIVE, "defaulted", false);
        when(profileService.saveProfile("CONSERVATIVE", 20.0)).thenReturn(saved);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.saveProfile(Map.of("riskTolerance", "CONSERVATIVE", "capitalAmount", 20.0));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(UserRiskProfile.CONSERVATIVE, response.getBody().getData().get("riskTolerance"));
    }
}
