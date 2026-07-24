package com.planning.platform.employee.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.planning.dto.WeekPlanSaveReqDTO;
import com.planning.platform.planning.service.WeekPlanService;
import com.planning.platform.planning.vo.WeekPlanVO.ActionVO;
import com.planning.platform.planning.vo.WeekPlanVO.DetailVO;
import com.planning.platform.planning.vo.WeekPlanVO.ParentOptionVO;
import com.planning.platform.planning.vo.WeekPlanVO.SummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/employee/week-plans")
public class EmployeeWeekPlanController {

    private final AuthService authService;
    private final WeekPlanService weekPlanService;

    @GetMapping
    public ApiResult<List<SummaryVO>> list(Authentication authentication,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.employeePlans(user, status, weekStart));
    }

    @GetMapping("/parent-options")
    public ApiResult<List<ParentOptionVO>> parentOptions(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.parentOptions(user));
    }

    @GetMapping("/{id}")
    public ApiResult<DetailVO> detail(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.employeeDetail(user, id));
    }

    @PostMapping
    public ApiResult<DetailVO> create(Authentication authentication,
                                      @Valid @RequestBody WeekPlanSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.createDraft(user, request));
    }

    @PutMapping("/{id}")
    public ApiResult<DetailVO> update(Authentication authentication,
                                      @PathVariable Long id,
                                      @Valid @RequestBody WeekPlanSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.updateDraft(user, id, request));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<ActionVO> submit(Authentication authentication,
                                      @PathVariable Long id,
                                      @RequestParam Integer versionNo) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.submit(user, id, versionNo));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResult<ActionVO> withdraw(Authentication authentication,
                                        @PathVariable Long id,
                                        @RequestParam Integer versionNo) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.withdraw(user, id, versionNo));
    }

    @DeleteMapping("/{id}")
    public ApiResult<ActionVO> delete(Authentication authentication,
                                      @PathVariable Long id,
                                      @RequestParam Integer versionNo) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(weekPlanService.deleteDraft(user, id, versionNo));
    }
}
