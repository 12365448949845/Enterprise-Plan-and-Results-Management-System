package com.planning.platform.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AiModels {

    public static final String MONTH_PLAN_DRAFT = "MONTH_PLAN_DRAFT";
    public static final String MONTH_PLAN_ITEM_OPTIMIZE = "MONTH_PLAN_ITEM_OPTIMIZE";
    public static final String MONTH_PLAN_CHECK = "MONTH_PLAN_CHECK";
    public static final String WEEK_PLAN_DRAFT = "WEEK_PLAN_DRAFT";
    public static final String WEEK_PLAN_ADJUST = "WEEK_PLAN_ADJUST";
    public static final String DAY_PLAN_DRAFT = "DAY_PLAN_DRAFT";
    public static final String DAY_PLAN_ADJUST = "DAY_PLAN_ADJUST";
    public static final String NOTICE = "AI 建议仅供参考，应用后仍需员工确认和提交";

    private AiModels() {
    }

    public record PlanItem(
            String workType,
            @Size(max = 120) String taskName,
            @Size(max = 5000) String taskContent,
            @Size(max = 500) String deliverable,
            LocalDate deadline,
            @DecimalMin("0.01") @DecimalMax("100") BigDecimal performanceWeight
    ) {
    }

    public record PlanForm(
            @Size(max = 5000) String summary,
            @Valid @Size(max = 20) List<PlanItem> items
    ) {
    }

    public record GenerateRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String planMonth,
            @NotBlank @Size(max = 5000) String intentText,
            @Valid PlanForm currentForm,
            @Size(max = 500) String jobDescription
    ) {
    }

    public record OptimizeRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String planMonth,
            @Size(max = 5000) String summary,
            @NotNull @Valid PlanItem item,
            @Size(max = 1000) String instruction,
            @Size(max = 500) String jobDescription
    ) {
    }

    public record CheckRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String planMonth,
            @NotNull @Valid PlanForm currentForm,
            @Size(max = 500) String jobDescription
    ) {
    }

    public record SuggestionActionRequest(
            @NotBlank String actionCode,
            @Size(max = 100) List<@Size(max = 200) String> appliedFields,
            @Size(max = 64) String beforeHash,
            @Size(max = 64) String afterHash
    ) {
    }

    public record ContextResponse(
            boolean enabled,
            String providerCode,
            String modelName,
            List<String> availableContext,
            List<String> missingContext,
            int historyPlanCount,
            Map<String, Integer> remainingCalls,
            String notice
    ) {
    }

    public record GenerateResponse(
            String suggestionId,
            String summary,
            List<PlanItem> items,
            List<String> warnings,
            List<String> missingContext,
            String notice
    ) {
    }

    public record OptimizeResponse(
            String suggestionId,
            PlanItem item,
            List<String> warnings,
            String notice
    ) {
    }

    public record CheckIssue(
            String code,
            String level,
            String fieldPath,
            String message,
            String suggestion
    ) {
    }

    public record CheckResponse(
            String suggestionId,
            List<CheckIssue> issues,
            String notice
    ) {
    }

    public record PlanContextResponse(
            Long id,
            Long orgId,
            String orgName,
            String planMonth,
            String departmentGoal,
            String leaderRequirement,
            Integer versionNo,
            String updatedAt
    ) {
    }

    public record SavePlanContextRequest(
            @NotNull Long orgId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String planMonth,
            @Size(max = 10000) String departmentGoal,
            @Size(max = 10000) String leaderRequirement,
            @NotNull Integer versionNo
    ) {
    }
}
