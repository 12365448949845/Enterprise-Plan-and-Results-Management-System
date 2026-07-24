package com.planning.platform.employee.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.employee.service.EmployeeAppealPackageService;
import com.planning.platform.employee.service.EmployeeService;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/employee")
public class EmployeeController {

    private final AuthService authService;
    private final EmployeeService employeeService;
    private final EmployeeAppealPackageService employeeAppealPackageService;
    private final PerformanceRoleGuard roleGuard;

    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard(Authentication authentication,
                                                    @RequestParam String month) {
        return ApiResult.ok(employeeService.dashboard(user(authentication), month));
    }

    @GetMapping("/month-plans/{id}")
    public ApiResult<Map<String, Object>> monthPlanDetail(Authentication authentication,
                                                          @PathVariable Long id) {
        return ApiResult.ok(employeeService.monthPlanDetail(user(authentication), id));
    }

    @PostMapping("/month-plans/draft")
    public ApiResult<Map<String, Object>> createMonthPlanDraft(Authentication authentication,
                                                               @Valid @RequestBody SaveMonthPlanDraftReq request) {
        return ApiResult.ok(employeeService.createMonthPlanDraft(user(authentication), request));
    }

    @PostMapping("/month-plans/{id}/draft")
    public ApiResult<Map<String, Object>> saveMonthPlanDraft(Authentication authentication,
                                                             @PathVariable Long id,
                                                             @Valid @RequestBody SaveMonthPlanDraftReq request) {
        return ApiResult.ok(employeeService.saveMonthPlanDraft(user(authentication), id, request));
    }

    @PostMapping("/month-plans/{id}/submit")
    public ApiResult<Map<String, Object>> submitMonthPlan(Authentication authentication,
                                                          @PathVariable Long id) {
        return ApiResult.ok(employeeService.submitMonthPlan(user(authentication), id));
    }

    @PostMapping("/month-plans/{id}/withdraw")
    public ApiResult<Map<String, Object>> withdrawMonthPlan(Authentication authentication,
                                                            @PathVariable Long id) {
        return ApiResult.ok(employeeService.withdrawMonthPlan(user(authentication), id));
    }

    @PostMapping("/month-plans/{id}/extra-items")
    public ApiResult<Map<String, Object>> submitExtraMonthPlanItem(Authentication authentication,
                                                                   @PathVariable Long id,
                                                                   @RequestParam(required = false) Long aiReviewId,
                                                                   @Valid @RequestBody SaveMonthPlanItemReq request) {
        return ApiResult.ok(employeeService.submitExtraMonthPlanItem(user(authentication), id, request, aiReviewId));
    }

    @PostMapping("/month-plans/{planId}/extra-items/{itemId}/draft")
    public ApiResult<Map<String, Object>> saveExtraMonthPlanItemDraft(Authentication authentication,
                                                                      @PathVariable Long planId,
                                                                      @PathVariable Long itemId,
                                                                      @Valid @RequestBody SaveMonthPlanItemReq request) {
        return ApiResult.ok(employeeService.saveExtraMonthPlanItemDraft(user(authentication), planId, itemId, request));
    }

    @PostMapping("/month-plans/{planId}/extra-items/{itemId}/submit")
    public ApiResult<Map<String, Object>> submitExtraMonthPlanItemDraft(Authentication authentication,
                                                                        @PathVariable Long planId,
                                                                        @PathVariable Long itemId) {
        return ApiResult.ok(employeeService.submitExtraMonthPlanItemDraft(user(authentication), planId, itemId));
    }

    @PostMapping("/month-plans/{planId}/extra-items/{itemId}/withdraw")
    public ApiResult<Map<String, Object>> withdrawExtraMonthPlanItem(Authentication authentication,
                                                                    @PathVariable Long planId,
                                                                    @PathVariable Long itemId) {
        return ApiResult.ok(employeeService.withdrawExtraMonthPlanItem(user(authentication), planId, itemId));
    }

    @DeleteMapping("/month-plans/{id}/items/{itemId}")
    public ApiResult<Void> deleteMonthPlanItem(Authentication authentication,
                                               @PathVariable Long id,
                                               @PathVariable Long itemId) {
        employeeService.deleteMonthPlanItem(user(authentication), id, itemId);
        return ApiResult.ok(null);
    }

    @GetMapping("/day-plans/detail")
    public ApiResult<Map<String, Object>> dayPlanDetail(Authentication authentication,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.ok(employeeService.dayPlanDetail(user(authentication), date));
    }

    @PostMapping("/day-plans/draft")
    public ApiResult<Map<String, Object>> saveDayPlanDraft(Authentication authentication,
                                                           @Valid @RequestBody SaveDayPlanDraftReq request) {
        return ApiResult.ok(employeeService.saveDayPlanDraft(user(authentication), request, false));
    }

    @PostMapping("/day-plans/submit")
    public ApiResult<Map<String, Object>> submitDayPlan(Authentication authentication,
                                                        @Valid @RequestBody SaveDayPlanDraftReq request) {
        return ApiResult.ok(employeeService.saveDayPlanDraft(user(authentication), request, true));
    }

    @PostMapping("/day-plans/{id}/withdraw")
    public ApiResult<Map<String, Object>> withdrawDayPlan(Authentication authentication,
                                                          @PathVariable Long id) {
        return ApiResult.ok(employeeService.withdrawDayPlan(user(authentication), id));
    }

    @GetMapping("/results/submit/options")
    public ApiResult<Map<String, Object>> resultSubmitOptions(Authentication authentication) {
        return ApiResult.ok(employeeService.resultSubmitOptions(user(authentication)));
    }

    @GetMapping("/results/{id}")
    public ApiResult<Map<String, Object>> resultDetail(Authentication authentication, @PathVariable Long id) {
        return ApiResult.ok(employeeService.resultDetail(user(authentication), id));
    }

    @PostMapping("/results/submit")
    public ApiResult<Map<String, Object>> submitResult(Authentication authentication,
                                                       @RequestParam Long monthPlanId,
                                                       @RequestParam(required = false) Long monthPlanItemId,
                                                       @RequestParam @Min(value = 0, message = "成果完成比例不能小于0")
                                                       @Max(value = 100, message = "成果完成比例不能大于100") Integer completionRate,
                                                       @RequestParam(required = false)
                                                       @Size(max = 5000, message = "成果说明不能超过5000个字符") String description,
                                                       @RequestParam(required = false) Long aiReviewId,
                                                       @RequestPart(required = false) MultipartFile file) {
        return ApiResult.ok(employeeService.submitResult(user(authentication), monthPlanId, monthPlanItemId,
                completionRate, description, file, aiReviewId));
    }

    @GetMapping("/results/{resultId}/evidence/{evidenceId}")
    public ResponseEntity<Resource> downloadEvidence(Authentication authentication,
                                                     @PathVariable Long resultId,
                                                     @PathVariable Long evidenceId) {
        return employeeService.downloadEvidence(user(authentication), resultId, evidenceId);
    }

    @GetMapping("/performance-evidence")
    public ApiResult<Map<String, Object>> performanceEvidence(Authentication authentication,
                                                              @RequestParam(defaultValue = "month") String periodType) {
        return ApiResult.ok(employeeService.performanceEvidence(user(authentication), periodType));
    }

    @PostMapping("/performance-evidence/export")
    public ApiResult<Map<String, Object>> exportPerformanceEvidence(Authentication authentication,
                                                                    @Valid @RequestBody EmployeeExportReq request) {
        return ApiResult.ok(employeeService.exportPerformanceEvidence(user(authentication), request));
    }

    @GetMapping("/export-tasks/{id}/download")
    public ResponseEntity<Resource> downloadExport(Authentication authentication, @PathVariable String id) {
        return employeeService.downloadExport(user(authentication), id);
    }

    @GetMapping("/appeals")
    public ApiResult<Map<String, Object>> appeals(Authentication authentication) {
        return ApiResult.ok(employeeService.appeals(user(authentication)));
    }

    @GetMapping("/appeals/options")
    public ApiResult<List<Map<String, Object>>> appealOptions(Authentication authentication) {
        return ApiResult.ok(employeeService.appealOptions(user(authentication)));
    }

    @GetMapping("/appeals/{id}/package")
    public ResponseEntity<Resource> downloadAppealPackage(Authentication authentication, @PathVariable Long id) {
        return employeeAppealPackageService.download(user(authentication), id);
    }

    @PostMapping("/appeals")
    public ApiResult<Map<String, Object>> createAppeal(Authentication authentication,
                                                       @Valid @RequestBody CreateAppealReq request) {
        return ApiResult.ok(employeeService.createAppeal(user(authentication), request));
    }

    @PostMapping("/plan-adjustments")
    public ApiResult<Map<String, Object>> createPlanAdjustment(Authentication authentication,
                                                               @Valid @RequestBody CreatePlanAdjustmentReq request) {
        return ApiResult.ok(employeeService.createPlanAdjustment(user(authentication), request));
    }

    private AuthUser user(Authentication authentication) {
        AuthUser user = authService.requireAuthUser(authentication);
        roleGuard.requireEmployeeModule(user);
        return user;
    }

    @Data
    public static class SaveMonthPlanDraftReq {
        @Size(max = 7, message = "计划月份格式不正确")
        private String planMonth;

        @Size(max = 5000, message = "月计划说明不能超过5000个字符")
        private String summary;

        private List<@Valid SaveMonthPlanItemReq> items;
    }

    @Data
    public static class SaveMonthPlanItemReq {
        private Long id;

        @Size(max = 120, message = "任务名称不能超过120个字符")
        private String taskName;

        @Size(max = 5000, message = "任务内容不能超过5000个字符")
        private String taskContent;

        @Size(max = 500, message = "交付物不能超过500个字符")
        private String deliverable;
        private LocalDate deadline;

        @DecimalMin(value = "0.01", message = "绩效权重必须大于0")
        private BigDecimal performanceWeight;
    }

    @Data
    public static class SaveDayPlanDraftReq {
        private Long id;
        @NotNull(message = "计划日期不能为空")
        private LocalDate planDate;
        private Long relatedMonthPlanItemId;

        @Size(max = 5000, message = "日计划内容不能超过5000个字符")
        private String content;

        @Size(max = 500, message = "日计划备注不能超过500个字符")
        private String remark;
    }

    @Data
    public static class CreateAppealReq {
        @NotNull(message = "请选择申诉关联成果")
        private Long relatedResultId;

        @NotBlank(message = "申诉标题不能为空")
        @Size(max = 120, message = "申诉标题不能超过120个字符")
        private String title;

        @NotBlank(message = "申诉理由不能为空")
        @Size(max = 1000, message = "申诉理由不能超过1000个字符")
        private String reason;
    }

    @Data
    public static class CreatePlanAdjustmentReq {
        @NotBlank(message = "计划类型不能为空")
        @Size(max = 20, message = "计划类型不能超过20个字符")
        private String planType;

        @NotNull(message = "请选择需要调整的月计划")
        private Long planId;

        @NotBlank(message = "调整类型不能为空")
        @Size(max = 30, message = "调整类型不能超过30个字符")
        private String adjustmentType;

        @NotBlank(message = "调整原因不能为空")
        @Size(max = 1000, message = "调整原因不能超过1000个字符")
        private String reason;

        @Size(max = 1000, message = "影响说明不能超过1000个字符")
        private String impactText;
    }

    @Data
    public static class EmployeeExportReq {
        @Size(max = 20, message = "导出周期类型不能超过20个字符")
        private String periodType;

        @NotEmpty(message = "请至少选择一种导出格式")
        @Size(max = 3, message = "导出格式最多选择3种")
        private List<@NotBlank(message = "导出格式不能为空") String> formats;
        private Boolean includeEvidence;
    }
}
