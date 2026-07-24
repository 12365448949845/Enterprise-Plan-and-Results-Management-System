package com.planning.platform.ai.controller;

import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.model.PlanDraftAiModels;
import com.planning.platform.ai.service.MonthPlanAiService;
import com.planning.platform.ai.service.PlanDraftAiService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/employee/ai")
public class EmployeeAiController {

    private final AuthService authService;
    private final PerformanceRoleGuard roleGuard;
    private final MonthPlanAiService aiService;
    private final PlanDraftAiService planDraftAiService;

    @GetMapping("/month-plans/context")
    public ApiResult<AiModels.ContextResponse> context(Authentication authentication,
                                                       @RequestParam String planMonth) {
        return ApiResult.ok(aiService.context(user(authentication), planMonth));
    }

    @PostMapping("/month-plans/generate")
    public ApiResult<AiModels.GenerateResponse> generate(Authentication authentication,
                                                         @Valid @RequestBody AiModels.GenerateRequest request) {
        return ApiResult.ok(aiService.generate(user(authentication), request));
    }

    @PostMapping("/month-plans/items/optimize")
    public ApiResult<AiModels.OptimizeResponse> optimize(Authentication authentication,
                                                         @Valid @RequestBody AiModels.OptimizeRequest request) {
        return ApiResult.ok(aiService.optimize(user(authentication), request));
    }

    @PostMapping("/month-plans/check")
    public ApiResult<AiModels.CheckResponse> check(Authentication authentication,
                                                   @Valid @RequestBody AiModels.CheckRequest request) {
        return ApiResult.ok(aiService.check(user(authentication), request));
    }

    @GetMapping("/week-plans/context")
    public ApiResult<PlanDraftAiModels.ContextResponse> weekContext(Authentication authentication,
                                                                    @RequestParam LocalDate weekStart) {
        return ApiResult.ok(planDraftAiService.weekContext(user(authentication), weekStart));
    }

    @PostMapping("/week-plans/generate")
    public ApiResult<PlanDraftAiModels.WeekDraft> generateWeek(Authentication authentication,
                                                               @Valid @RequestBody PlanDraftAiModels.WeekGenerateRequest request) {
        return ApiResult.ok(planDraftAiService.generateWeek(user(authentication), request));
    }

    @PostMapping("/week-plans/adjust")
    public ApiResult<PlanDraftAiModels.WeekDraft> adjustWeek(Authentication authentication,
                                                             @Valid @RequestBody PlanDraftAiModels.WeekAdjustRequest request) {
        return ApiResult.ok(planDraftAiService.adjustWeek(user(authentication), request));
    }

    @GetMapping("/day-plans/context")
    public ApiResult<PlanDraftAiModels.ContextResponse> dayContext(Authentication authentication,
                                                                   @RequestParam LocalDate planDate) {
        return ApiResult.ok(planDraftAiService.dayContext(user(authentication), planDate));
    }

    @PostMapping("/day-plans/generate")
    public ApiResult<PlanDraftAiModels.DayDraft> generateDay(Authentication authentication,
                                                              @Valid @RequestBody PlanDraftAiModels.DayGenerateRequest request) {
        return ApiResult.ok(planDraftAiService.generateDay(user(authentication), request));
    }

    @PostMapping("/day-plans/adjust")
    public ApiResult<PlanDraftAiModels.DayDraft> adjustDay(Authentication authentication,
                                                            @Valid @RequestBody PlanDraftAiModels.DayAdjustRequest request) {
        return ApiResult.ok(planDraftAiService.adjustDay(user(authentication), request));
    }

    @PostMapping("/suggestions/{suggestionId}/actions")
    public ApiResult<Void> recordAction(Authentication authentication,
                                        @PathVariable String suggestionId,
                                        @Valid @RequestBody AiModels.SuggestionActionRequest request) {
        planDraftAiService.recordAction(user(authentication), suggestionId, request);
        return ApiResult.ok(null);
    }

    private AuthUser user(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        roleGuard.requireEmployeeModule(user);
        return user;
    }
}
