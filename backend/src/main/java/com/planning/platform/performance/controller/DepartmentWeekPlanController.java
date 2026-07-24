package com.planning.platform.performance.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.planning.service.WeekPlanService;
import com.planning.platform.planning.vo.WeekPlanVO.DetailVO;
import com.planning.platform.planning.vo.WeekPlanVO.SummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/department/week-plans")
public class DepartmentWeekPlanController {

    private final AuthService authService;
    private final WeekPlanService weekPlanService;

    @GetMapping
    public ApiResult<List<SummaryVO>> list(Authentication authentication,
                                           @RequestParam(required = false) Long deptId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.departmentPlans(user, deptId, status, weekStart));
    }

    @GetMapping("/{id}")
    public ApiResult<DetailVO> detail(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.departmentDetail(user, id));
    }
}
