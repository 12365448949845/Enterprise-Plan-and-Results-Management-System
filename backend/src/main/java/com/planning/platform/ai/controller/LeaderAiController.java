package com.planning.platform.ai.controller;

import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.service.MonthPlanAiService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/leader/ai")
public class LeaderAiController {

    private final AuthService authService;
    private final PerformanceRoleGuard roleGuard;
    private final MonthPlanAiService aiService;

    @GetMapping("/month-plan-context")
    public ApiResult<AiModels.PlanContextResponse> context(Authentication authentication,
                                                           @RequestParam String planMonth,
                                                           @RequestParam Long orgId) {
        return ApiResult.ok(aiService.getPlanContext(user(authentication), orgId, planMonth));
    }

    @PutMapping("/month-plan-context")
    public ApiResult<AiModels.PlanContextResponse> save(Authentication authentication,
                                                        @Valid @RequestBody AiModels.SavePlanContextRequest request) {
        return ApiResult.ok(aiService.savePlanContext(user(authentication), request));
    }

    private AuthUser user(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        roleGuard.requireLeaderModule(user);
        return user;
    }
}
