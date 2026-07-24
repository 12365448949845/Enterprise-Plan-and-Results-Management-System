package com.planning.platform.planning.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.service.DayPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/planning/day-plans")
public class DayPlanController {

    private final AuthService authService;
    private final DayPlanService dayPlanService;

    @GetMapping
    public ApiResult<List<BizDayPlan>> list(Authentication authentication,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Boolean mine) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(dayPlanService.list(user, status, startDate, endDate, keyword, mine));
    }

    @GetMapping("/my")
    public ApiResult<List<BizDayPlan>> my(Authentication authentication,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(required = false) String keyword) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(dayPlanService.list(user, status, startDate, endDate, keyword, true));
    }

}
