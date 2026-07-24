package com.planning.platform.performance.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.employee.service.EmployeeAppealPackageService;
import com.planning.platform.performance.dto.AcceptanceStandardSaveReqDTO;
import com.planning.platform.performance.dto.BatchActionReqDTO;
import com.planning.platform.performance.dto.DeliverableTemplateSaveReqDTO;
import com.planning.platform.performance.dto.ExportTaskCreateReqDTO;
import com.planning.platform.performance.dto.PerformanceActionReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSaveReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSimulateReqDTO;
import com.planning.platform.performance.service.DepartmentPerformanceService;
import com.planning.platform.performance.service.ResultEvidenceAccessService;
import com.planning.platform.performance.vo.PerformanceVO.AcceptanceStandardVO;
import com.planning.platform.performance.vo.PerformanceVO.ActionResultVO;
import com.planning.platform.performance.vo.PerformanceVO.AppealProcessVO;
import com.planning.platform.performance.vo.PerformanceVO.DeliverableTemplateVO;
import com.planning.platform.performance.vo.PerformanceVO.DepartmentDayPlanReviewVO;
import com.planning.platform.performance.vo.PerformanceVO.DepartmentDashboardVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportDownloadVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportTaskVO;
import com.planning.platform.performance.vo.PerformanceVO.LedgerItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalPageVO;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
import com.planning.platform.performance.vo.PerformanceVO.ResultConfirmItemVO;
import com.planning.platform.performance.vo.PerformanceVO.ScoreRuleVO;
import com.planning.platform.performance.vo.PerformanceVO.ScoreSimulationVO;
import com.planning.platform.performance.vo.PerformanceVO.TodoItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/department")
public class DepartmentPerformanceController {

    private final AuthService authService;
    private final DepartmentPerformanceService departmentPerformanceService;
    private final ResultEvidenceAccessService resultEvidenceAccessService;
    private final EmployeeAppealPackageService employeeAppealPackageService;

    @GetMapping("/org-tree")
    public ApiResult<List<OrgNodeVO>> orgTree(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.orgTree(user));
    }

    @GetMapping("/dashboard")
    public ApiResult<DepartmentDashboardVO> dashboard(Authentication authentication,
                                                     @RequestParam(required = false) Long orgId,
                                                     @RequestParam(required = false) String periodType,
                                                     @RequestParam(required = false) String periodMonth) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.dashboard(user, orgId, periodType, periodMonth));
    }

    @GetMapping("/month-plan-approvals")
    public ApiResult<List<MonthPlanApprovalItemVO>> monthPlanApprovals(Authentication authentication,
                                                                      @RequestParam(required = false) Integer planYear,
                                                                      @RequestParam(required = false) Integer planMonth,
                                                                      @RequestParam(required = false) Long orgId,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String keyword) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.monthPlanApprovals(user, planYear, planMonth, orgId, status, keyword));
    }

    @GetMapping("/month-plan-approvals/page")
    public ApiResult<MonthPlanApprovalPageVO> monthPlanApprovalsPage(Authentication authentication,
                                                                    @RequestParam(required = false) Integer planYear,
                                                                    @RequestParam(required = false) Integer planMonth,
                                                                    @RequestParam(required = false) Long orgId,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) String keyword,
                                                                    @RequestParam(defaultValue = "1") Integer pageNo,
                                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.monthPlanApprovalsPage(
                user, planYear, planMonth, orgId, status, keyword, pageNo, pageSize));
    }

    @GetMapping("/month-plan-approvals/{id}")
    public ApiResult<MonthPlanApprovalItemVO> monthPlanApprovalDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.monthPlanApprovalDetail(user, id));
    }

    @GetMapping("/result-confirms")
    public ApiResult<List<ResultConfirmItemVO>> resultConfirms(Authentication authentication,
                                                              @RequestParam(required = false) Long orgId,
                                                              @RequestParam(required = false) String periodMonth,
                                                              @RequestParam(required = false) String confirmStatus,
                                                              @RequestParam(required = false) String keyword) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.resultConfirms(user, orgId, periodMonth, confirmStatus, keyword));
    }

    @GetMapping("/result-confirms/{id}")
    public ApiResult<ResultConfirmItemVO> resultConfirmDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.resultConfirmDetail(user, id));
    }

    @GetMapping("/result-confirms/{resultId}/evidence/{evidenceId}")
    public ResponseEntity<Resource> downloadResultEvidence(Authentication authentication,
                                                           @PathVariable Long resultId,
                                                           @PathVariable Long evidenceId) {
        AuthUser user = authService.requireAuthUser(authentication);
        return resultEvidenceAccessService.downloadForDepartment(user, resultId, evidenceId);
    }

    @PostMapping("/result-confirms/{id}/confirm")
    public ApiResult<ActionResultVO> confirmResult(Authentication authentication,
                                                  @PathVariable String id,
                                                  @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.confirmResult(user, id, request));
    }

    @PostMapping("/result-confirms/{id}/reject")
    public ApiResult<ActionResultVO> rejectResult(Authentication authentication,
                                                 @PathVariable String id,
                                                 @Valid @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.rejectResult(user, id, request));
    }

    @GetMapping("/todos")
    public ApiResult<List<TodoItemVO>> todos(Authentication authentication,
                                            @RequestParam(required = false) String sceneCode,
                                            @RequestParam(required = false) String status) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.todos(user, sceneCode, status));
    }

    @GetMapping("/day-plan-reviews/{id}")
    public ApiResult<DepartmentDayPlanReviewVO> dayPlanReviewDetail(Authentication authentication,
                                                                   @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.dayPlanReviewDetail(user, id));
    }

    @PostMapping("/day-plan-reviews/{id}/approve")
    public ApiResult<ActionResultVO> approveDayPlanReview(Authentication authentication,
                                                         @PathVariable String id,
                                                         @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.approveDayPlanReview(user, id, request));
    }

    @PostMapping("/day-plan-reviews/{id}/reject")
    public ApiResult<ActionResultVO> rejectDayPlanReview(Authentication authentication,
                                                        @PathVariable String id,
                                                        @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.rejectDayPlanReview(user, id, request));
    }

    @PostMapping("/todos/{id}/remind")
    public ApiResult<ActionResultVO> remindTodo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.remindTodo(user, id));
    }

    @PostMapping("/todos/{id}/read")
    public ApiResult<ActionResultVO> readTodo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.readTodo(user, id));
    }

    @PostMapping("/todos/{id}/escalate")
    public ApiResult<ActionResultVO> escalateTodo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.escalateTodo(user, id));
    }

    @PostMapping("/todos/{id}/done")
    public ApiResult<ActionResultVO> doneTodo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.doneTodo(user, id));
    }

    @PostMapping("/todos/batch-remind")
    public ApiResult<List<ActionResultVO>> batchRemindTodos(Authentication authentication,
                                                           @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.batchTodoAction(user, request, "NOTIFICATION_BATCH_REMIND"));
    }

    @PostMapping("/todos/batch-escalate")
    public ApiResult<List<ActionResultVO>> batchEscalateTodos(Authentication authentication,
                                                             @Valid @RequestBody BatchActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.batchTodoAction(user, request, "NOTIFICATION_BATCH_ESCALATE"));
    }

    @GetMapping("/appeals/{id}")
    public ApiResult<AppealProcessVO> appealDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.appealDetail(user, id));
    }

    @PostMapping("/appeals/{id}/accept")
    public ApiResult<ActionResultVO> acceptAppeal(Authentication authentication,
                                                   @PathVariable String id,
                                                   @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.acceptAppeal(user, id, request));
    }

    @PostMapping("/appeals/{id}/resolve")
    public ApiResult<ActionResultVO> resolveAppeal(Authentication authentication,
                                                    @PathVariable String id,
                                                    @RequestBody PerformanceActionReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.resolveAppeal(user, id, request));
    }

    @GetMapping("/appeals/{id}/package")
    public ResponseEntity<Resource> downloadAppealPackage(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return employeeAppealPackageService.downloadForHandler(user, id);
    }

    @GetMapping("/templates")
    public ApiResult<List<DeliverableTemplateVO>> templates(Authentication authentication,
                                                           @RequestParam(required = false) Long orgId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.templates(user, orgId, status, keyword));
    }

    @PostMapping("/templates")
    public ApiResult<DeliverableTemplateVO> createTemplate(Authentication authentication,
                                                         @Valid @RequestBody DeliverableTemplateSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveTemplate(user, null, request));
    }

    @PutMapping("/templates/{id}")
    public ApiResult<DeliverableTemplateVO> updateTemplate(Authentication authentication,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody DeliverableTemplateSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveTemplate(user, id, request));
    }

    @PostMapping("/templates/{id}/enable")
    public ApiResult<ActionResultVO> enableTemplate(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.toggleTemplate(user, id, true));
    }

    @PostMapping("/templates/{id}/disable")
    public ApiResult<ActionResultVO> disableTemplate(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.toggleTemplate(user, id, false));
    }

    @GetMapping("/acceptance-standards")
    public ApiResult<List<AcceptanceStandardVO>> acceptanceStandards(Authentication authentication,
                                                                    @RequestParam(required = false) Long templateId,
                                                                    @RequestParam(required = false) String status) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.acceptanceStandards(user, templateId, status));
    }

    @PostMapping("/acceptance-standards")
    public ApiResult<AcceptanceStandardVO> createAcceptanceStandard(Authentication authentication,
                                                                   @Valid @RequestBody AcceptanceStandardSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveAcceptanceStandard(user, null, request));
    }

    @PutMapping("/acceptance-standards/{id}")
    public ApiResult<AcceptanceStandardVO> updateAcceptanceStandard(Authentication authentication,
                                                                   @PathVariable Long id,
                                                                   @Valid @RequestBody AcceptanceStandardSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveAcceptanceStandard(user, id, request));
    }

    @PostMapping("/acceptance-standards/{id}/enable")
    public ApiResult<ActionResultVO> enableAcceptanceStandard(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.toggleAcceptanceStandard(user, id, true));
    }

    @PostMapping("/acceptance-standards/{id}/disable")
    public ApiResult<ActionResultVO> disableAcceptanceStandard(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.toggleAcceptanceStandard(user, id, false));
    }

    @GetMapping("/score-rules")
    public ApiResult<List<ScoreRuleVO>> scoreRules(Authentication authentication,
                                                  @RequestParam(required = false) Long orgId,
                                                  @RequestParam(required = false) String status) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.scoreRules(user, orgId, status));
    }

    @PostMapping("/score-rules")
    public ApiResult<ScoreRuleVO> createScoreRule(Authentication authentication,
                                                @Valid @RequestBody ScoreRuleSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveScoreRule(user, null, request));
    }

    @PutMapping("/score-rules/{id}")
    public ApiResult<ScoreRuleVO> updateScoreRule(Authentication authentication,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ScoreRuleSaveReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.saveScoreRule(user, id, request));
    }

    @PostMapping("/score-rules/{id}/enable")
    public ApiResult<ActionResultVO> enableScoreRule(Authentication authentication, @PathVariable Long id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.enableScoreRule(user, id));
    }

    @PostMapping("/score-rules/{id}/simulate")
    public ApiResult<ScoreSimulationVO> simulateScoreRule(Authentication authentication,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody ScoreRuleSimulateReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.simulateScoreRule(user, id, request));
    }

    @GetMapping("/department-ledgers")
    public ApiResult<List<LedgerItemVO>> departmentLedgers(Authentication authentication,
                                                          @RequestParam(required = false) Long orgId,
                                                          @RequestParam(required = false) String periodType,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                                                          @RequestParam(required = false) String employeeName) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.departmentLedgers(user, orgId, periodType, periodStart, periodEnd, employeeName));
    }

    @PostMapping("/department-ledgers/export")
    public ApiResult<ExportTaskVO> exportDepartmentLedger(Authentication authentication,
                                                          @Valid @RequestBody ExportTaskCreateReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.exportDepartmentLedger(user, request));
    }

    @GetMapping("/export-tasks")
    public ApiResult<List<ExportTaskVO>> exportTasks(Authentication authentication,
                                                    @RequestParam(required = false) String format,
                                                    @RequestParam(required = false) String status) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.exportTasks(user, format, status));
    }

    @GetMapping("/export-tasks/{id}")
    public ApiResult<ExportTaskVO> exportTaskDetail(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.exportTaskDetail(user, id));
    }

    @PostMapping("/export-tasks")
    public ApiResult<ExportTaskVO> createExportTask(Authentication authentication,
                                                   @Valid @RequestBody ExportTaskCreateReqDTO request) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.createExportTask(user, request));
    }

    @PostMapping("/export-tasks/{id}/check")
    public ApiResult<ActionResultVO> checkExportTask(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.checkExportTask(user, id));
    }

    @PostMapping("/export-tasks/{id}/retry")
    public ApiResult<ActionResultVO> retryExportTask(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.retryExportTask(user, id));
    }

    @GetMapping("/export-tasks/{id}/download-info")
    public ApiResult<ExportDownloadVO> downloadInfo(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        return ApiResult.ok(departmentPerformanceService.exportDownloadInfo(user, id));
    }

    @GetMapping("/export-tasks/{id}/download")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable String id) {
        AuthUser user = authService.requireAuthUser(authentication);
        ExportDownloadVO info = departmentPerformanceService.exportDownloadInfo(user, id);
        Resource resource = departmentPerformanceService.downloadExport(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.fileName() + "\"")
                .body(resource);
    }
}
