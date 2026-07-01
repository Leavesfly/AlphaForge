package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.config.EnvVarProvider;
import io.leavesfly.alphaforge.config.SchedulerAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthController 认证 API 测试")
class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        EnvVarProvider envVarProvider = new EnvVarProvider();
        SchedulerAuthConfig schedulerAuthConfig = new SchedulerAuthConfig(envVarProvider);
        schedulerAuthConfig.setAuthPasswordRuntime("test123");
        schedulerAuthConfig.setAuthSecretRuntime("test-secret-key-for-jwt-signing-min-32-chars");
        schedulerAuthConfig.setAuthEnabledRuntime(true);
        controller = new AuthController(schedulerAuthConfig);
    }

    @Test
    @DisplayName("正确密码应返回 token")
    void loginWithValidPassword() {
        ResponseEntity<?> response = controller.login(Map.of("password", "test123"));
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("错误密码应返回 401")
    void loginWithInvalidPassword() {
        ResponseEntity<?> response = controller.login(Map.of("password", "wrong"));
        assertEquals(401, response.getStatusCode().value());
    }
}
