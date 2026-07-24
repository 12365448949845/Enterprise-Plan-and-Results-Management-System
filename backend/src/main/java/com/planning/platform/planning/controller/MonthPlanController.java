package com.planning.platform.planning.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.service.MonthPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/planning/month-plans")
public class MonthPlanController {

    private final AuthService authService;
    private final MonthPlanService monthPlanService;

    @GetMapping
    public ApiResult<List<BizMonthPlan>> list(Authentication authentication,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String planMonth,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Boolean mine) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(monthPlanService.list(user, status, planMonth, keyword, mine));
    }

}
