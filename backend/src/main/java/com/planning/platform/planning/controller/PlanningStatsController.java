package com.planning.platform.planning.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.planning.service.PlanningStatsService;
import com.planning.platform.planning.vo.PlanningStatsRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/planning")
public class PlanningStatsController {

    private final AuthService authService;
    private final PlanningStatsService planningStatsService;

    @GetMapping("/stats")
    public ApiResult<PlanningStatsRespVO> stats(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(planningStatsService.stats(user));
    }
}
