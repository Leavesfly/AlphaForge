package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.service.user.UserRiskProfileService;
import io.leavesfly.alphaforge.presentation.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户风险画像 REST API。
 *
 * <p>仅负责参数绑定，业务逻辑委托 {@link UserRiskProfileService}。</p>
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final UserRiskProfileService profileService;

    public ProfileController(UserRiskProfileService profileService) {
        this.profileService = profileService;
    }

    /** 获取画像视图（含档位/资金/仓位乘数/defaulted 标注） */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfileView()));
    }

    /**
     * 保存画像。
     * body: { "riskTolerance": "CONSERVATIVE|BALANCED|AGGRESSIVE", "capitalAmount": 20.0(万元,可选) }
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveProfile(@RequestBody Map<String, Object> body) {
        String riskTolerance = body.get("riskTolerance") != null ? String.valueOf(body.get("riskTolerance")) : null;
        Double capitalAmount = body.get("capitalAmount") != null
                ? Double.parseDouble(String.valueOf(body.get("capitalAmount")))
                : null;
        return ResponseEntity.ok(ApiResponse.ok(profileService.saveProfile(riskTolerance, capitalAmount), "画像已保存"));
    }
}
