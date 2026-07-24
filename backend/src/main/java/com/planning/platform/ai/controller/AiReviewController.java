package com.planning.platform.ai.controller;

import com.planning.platform.ai.model.AiReviewModels.ReviewVO;
import com.planning.platform.ai.model.AiReviewModels.CapabilityVO;
import com.planning.platform.ai.model.ExtraTaskPreviewReq;
import com.planning.platform.ai.service.AiReviewService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai/reviews")
public class AiReviewController {

    private final AuthService authService;
    private final PerformanceRoleGuard roleGuard;
    private final AiReviewService aiReviewService;

    @PostMapping("/plans/{bizType}/{bizId}/check")
    public ApiResult<ReviewVO> checkPlan(Authentication authentication,
                                         @PathVariable String bizType,
                                         @PathVariable Long bizId) {
        AuthUser user = user(authentication);
        roleGuard.requireEmployeeModule(user);
        return ApiResult.ok(aiReviewService.checkPlan(user, bizType, bizId));
    }

    @PostMapping("/plans/{bizType}/{bizId}/ensure")
    public ApiResult<ReviewVO> ensurePlan(Authentication authentication,
                                          @PathVariable String bizType,
                                          @PathVariable Long bizId) {
        AuthUser user = user(authentication);
        roleGuard.requireEmployeeModule(user);
        return ApiResult.ok(aiReviewService.ensurePlanReview(user, bizType, bizId));
    }

    @GetMapping("/capability")
    public ApiResult<CapabilityVO> capability(Authentication authentication) {
        AuthUser user = user(authentication);
        return ApiResult.ok(aiReviewService.capability(user));
    }

    @PostMapping("/results/preview")
    public ApiResult<ReviewVO> previewResult(Authentication authentication,
                                             @RequestParam Long monthPlanId,
                                             @RequestParam(required = false) Long monthPlanItemId,
                                             @RequestParam @Min(0) @Max(100) Integer completionRate,
                                             @RequestParam(required = false) String description,
                                             @RequestPart MultipartFile file) {
        AuthUser user = user(authentication);
        roleGuard.requireEmployeeModule(user);
        return ApiResult.ok(aiReviewService.previewResult(user, monthPlanId, monthPlanItemId,
                completionRate, description, file));
    }

    @PostMapping("/extra-tasks/{monthPlanId}/preview")
    public ApiResult<ReviewVO> previewExtraTask(Authentication authentication,
                                                @PathVariable Long monthPlanId,
                                                @Valid @RequestBody ExtraTaskPreviewReq request) {
        AuthUser user = user(authentication);
        roleGuard.requireEmployeeModule(user);
        return ApiResult.ok(aiReviewService.previewExtraTask(user, monthPlanId, request));
    }

    @GetMapping("/latest")
    public ApiResult<ReviewVO> latest(Authentication authentication,
                                      @RequestParam String bizType,
                                      @RequestParam Long bizId) {
        return ApiResult.ok(aiReviewService.latestForViewer(user(authentication), bizType, bizId));
    }

    private AuthUser user(Authentication authentication) {
        return authService.requireAuthUser(authentication);
    }
}
