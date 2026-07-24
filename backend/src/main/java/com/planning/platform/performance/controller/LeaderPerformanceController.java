package com.planning.platform.performance.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.performance.dto.BatchActionReqDTO;
import com.planning.platform.performance.dto.ExportTaskCreateReqDTO;
import com.planning.platform.performance.dto.PerformanceActionReqDTO;
import com.planning.platform.performance.service.LeaderPerformanceService;
import com.planning.platform.performance.service.DepartmentPerformanceService;
import com.planning.platform.performance.service.ResultEvidenceAccessService;
import com.planning.platform.performance.vo.PerformanceVO.ActionResultVO;
import com.planning.platform.performance.vo.PerformanceVO.DailyReviewItemVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportDownloadVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportTaskVO;
import com.planning.platform.performance.vo.PerformanceVO.ExtraMonthPlanApprovalVO;
import com.planning.platform.performance.vo.PerformanceVO.LeaderWorkbenchVO;
import com.planning.platform.performance.vo.PerformanceVO.LedgerItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalPageVO;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
import com.planning.platform.performance.vo.PerformanceVO.PlanAdjustmentItemVO;
import com.planning.platform.performance.vo.PerformanceVO.ResultSuggestionItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/leader")
public class LeaderPerformanceController {

    private final AuthService authService;
    private final LeaderPerformanceService leaderPerformanceService;
    private final DepartmentPerformanceService departmentPerformanceService;
    private final ResultEvidenceAccessService resultEvidenceAccessService;

    @GetMapping("/org-tree")
    public ApiResult<List<OrgNodeVO>> orgTree(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.orgTree(user));
    }

    @GetMapping("/workbench")
    public ApiResult<LeaderWorkbenchVO> workbench(Authentication authentication,
                                                 @RequestParam(required = false) Long scopeOrgId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @RequestParam(required = false) String periodMonth) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.workbench(user, scopeOrgId, date, periodMonth));
    }

    @GetMapping("/daily-reviews")
    public ApiResult<List<DailyReviewItemVO>> dailyReviews(Authentication authentication,
                                                          @RequestParam(required = false) Long scopeOrgId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                          @RequestParam(required = false) String reviewStatus,
                                                          @RequestParam(required = false) Boolean missingOnly) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.dailyReviews(user, scopeOrgId, startDate, endDate, reviewStatus, missingOnly));
    }

    @GetMapping("/daily-reviews/{id}")
    public ApiResult<DailyReviewItemVO> dailyReviewDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.dailyReviewDetail(user, id));
    }

    @PostMapping("/daily-reviews/{id}/comment")
    public ApiResult<ActionResultVO> commentDailyReview(Authentication authentication,
                                                       @PathVariable String id,
                                                       @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.commentDailyPlan(user, id, request));
    }

    @PostMapping("/daily-reviews/{id}/risk")
    public ApiResult<ActionResultVO> riskDailyReview(Authentication authentication,
                                                    @PathVariable String id,
                                                    @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.markDailyRisk(user, id, request));
    }

    @PostMapping("/daily-reviews/batch-comment")
    public ApiResult<List<ActionResultVO>> batchCommentDailyReviews(Authentication authentication,
                                                                   @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.batchCommentDailyPlans(user, request));
    }

    @PostMapping("/daily-reviews/batch-risk")
    public ApiResult<List<ActionResultVO>> batchRiskDailyReviews(Authentication authentication,
                                                                @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.batchMarkDailyRisks(user, request));
    }

    @GetMapping("/result-suggestions")
    public ApiResult<List<ResultSuggestionItemVO>> resultSuggestions(Authentication authentication,
                                                                   @RequestParam(required = false) Long scopeOrgId,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                                   @RequestParam(required = false) String suggestionStatus,
                                                                   @RequestParam(required = false) String evidenceStatus) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.resultSuggestions(
                user, scopeOrgId, startDate, endDate, suggestionStatus, evidenceStatus));
    }

    @GetMapping("/result-suggestions/{id}")
    public ApiResult<ResultSuggestionItemVO> resultSuggestionDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.resultSuggestionDetail(user, id));
    }

    @GetMapping("/result-suggestions/{resultId}/evidence/{evidenceId}")
    public ResponseEntity<Resource> downloadResultEvidence(Authentication authentication,
                                                           @PathVariable Long resultId,
                                                           @PathVariable Long evidenceId) {
        AuthUser user = authService.requireAuthUser(authentication);
        return resultEvidenceAccessService.downloadForLeader(user, resultId, evidenceId);
    }

    @PostMapping("/result-suggestions/{id}/suggest")
    public ApiResult<ActionResultVO> submitResultSuggestion(Authentication authentication,
                                                           @PathVariable String id,
                                                           @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.submitResultSuggestion(user, id, request));
    }

    @PostMapping("/result-suggestions/batch-suggest")
    public ApiResult<List<ActionResultVO>> batchResultSuggestion(Authentication authentication,
                                                                @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.batchSubmitResultSuggestions(user, request));
    }

    @GetMapping("/plan-adjustments")
    public ApiResult<List<PlanAdjustmentItemVO>> planAdjustments(Authentication authentication,
                                                               @RequestParam(required = false) Long scopeOrgId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String periodMonth) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.planAdjustments(user, scopeOrgId, status, periodMonth));
    }

    @GetMapping("/month-plan-approvals")
    public ApiResult<List<MonthPlanApprovalItemVO>> monthPlanApprovals(
            Authentication authentication,
            @RequestParam(required = false) Integer planYear,
            @RequestParam(required = false) Integer planMonth,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.leaderMonthPlanApprovals(
                user, planYear, planMonth, orgId, status, keyword));
    }

    @GetMapping("/month-plan-approvals/page")
    public ApiResult<MonthPlanApprovalPageVO> monthPlanApprovalsPage(
            Authentication authentication,
            @RequestParam(required = false) Integer planYear,
            @RequestParam(required = false) Integer planMonth,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.leaderMonthPlanApprovalsPage(
                user, planYear, planMonth, orgId, status, keyword, pageNo, pageSize));
    }

    @GetMapping("/month-plan-approvals/{id}")
    public ApiResult<MonthPlanApprovalItemVO> monthPlanApprovalDetail(
            Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.leaderMonthPlanApprovalDetail(user, id));
    }

    @PostMapping("/month-plan-approvals/{id}/approve")
    public ApiResult<ActionResultVO> approveMonthPlan(
            Authentication authentication, @PathVariable String id,
            @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.approveMonthPlan(user, id, request));
    }

    @PostMapping("/month-plan-approvals/{id}/reject")
    public ApiResult<ActionResultVO> rejectMonthPlan(
            Authentication authentication, @PathVariable String id,
            @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.rejectMonthPlan(user, id, request));
    }

    @PostMapping("/month-plan-approvals/batch-approve")
    public ApiResult<List<ActionResultVO>> batchApproveMonthPlans(
            Authentication authentication, @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.batchApproveMonthPlans(user, request));
    }

    @PostMapping("/month-plan-approvals/batch-reject")
    public ApiResult<List<ActionResultVO>> batchRejectMonthPlans(
            Authentication authentication, @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.batchRejectMonthPlans(user, request));
    }

    @GetMapping("/extra-month-plan-approvals")
    public ApiResult<List<ExtraMonthPlanApprovalVO>> extraMonthPlanApprovals(
            Authentication authentication,
            @RequestParam(required = false) Long scopeOrgId,
            @RequestParam(required = false) String status) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.extraMonthPlanApprovals(user, scopeOrgId, status));
    }

    @PostMapping("/extra-month-plan-approvals/{id}/approve")
    public ApiResult<ActionResultVO> approveExtraMonthPlanItem(Authentication authentication,
                                                               @PathVariable String id,
                                                               @RequestBody(required = false) PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.approveExtraMonthPlanItem(
                user, id, request == null ? new PerformanceActionReqDTO() : request));
    }

    @PostMapping("/extra-month-plan-approvals/{id}/reject")
    public ApiResult<ActionResultVO> rejectExtraMonthPlanItem(Authentication authentication,
                                                              @PathVariable String id,
                                                              @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.rejectExtraMonthPlanItem(user, id, request));
    }

    @GetMapping("/plan-adjustments/{id}")
    public ApiResult<PlanAdjustmentItemVO> planAdjustmentDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.planAdjustmentDetail(user, id));
    }

    @PostMapping("/plan-adjustments/{id}/process")
    public ApiResult<ActionResultVO> processPlanAdjustment(Authentication authentication,
                                                          @PathVariable String id,
                                                          @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.processPlanAdjustment(user, id, request));
    }

    @GetMapping("/team-ledgers")
    public ApiResult<List<LedgerItemVO>> teamLedgers(Authentication authentication,
                                                    @RequestParam(required = false) Long scopeOrgId,
                                                    @RequestParam(required = false) String periodType,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                                                    @RequestParam(required = false) String employeeName) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.teamLedgers(user, scopeOrgId, periodType, periodStart, periodEnd, employeeName));
    }

    @PostMapping("/team-ledgers/export")
    public ApiResult<ExportTaskVO> exportTeamLedgers(Authentication authentication,
                                                    @Valid @RequestBody ExportTaskCreateReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.exportTeamLedger(user, request));
    }

    @PostMapping("/export-tasks")
    public ApiResult<ExportTaskVO> createExportTask(Authentication authentication,
                                                    @Valid @RequestBody ExportTaskCreateReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.createExportTask(user, request));
    }

    @GetMapping("/export-tasks/{id}/download-info")
    public ApiResult<ExportDownloadVO> exportDownloadInfo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(leaderPerformanceService.exportDownloadInfo(user, id));
    }

    @GetMapping("/export-tasks/{id}/download")
    public ResponseEntity<Resource> downloadExport(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        ExportDownloadVO info = leaderPerformanceService.exportDownloadInfo(user, id);
        Resource resource = leaderPerformanceService.downloadExport(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.fileName() + "\"")
                .body(resource);
    }
}
