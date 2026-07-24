package com.planning.platform.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PlanDraftAiModels {

    private PlanDraftAiModels() {
    }

    public record WeekItem(
            Long monthPlanItemId,
            @Size(max = 5000) String content,
            @Size(max = 500) String deliverable,
            LocalDate plannedFinishDate
    ) {
    }

    public record WeekForm(@Valid @Size(max = 100) List<WeekItem> items) {
    }

    public record WeekGenerateRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotNull LocalDate weekStart,
            @NotBlank @Size(max = 5000) String intentText,
            @Valid WeekForm currentForm
    ) {
    }

    public record WeekAdjustRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotNull LocalDate weekStart,
            @NotNull @Valid WeekForm draft,
            @NotBlank @Size(max = 1000) String instruction,
            @Min(0) Integer targetItemIndex
    ) {
    }

    public record DayForm(
            Long relatedMonthPlanItemId,
            @Size(max = 5000) String content,
            @Size(max = 500) String remark
    ) {
    }

    public record DayGenerateRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotNull LocalDate planDate,
            @NotBlank @Size(max = 5000) String intentText,
            @Valid DayForm currentForm
    ) {
    }

    public record DayAdjustRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotNull LocalDate planDate,
            @NotNull @Valid DayForm draft,
            @NotBlank @Size(max = 1000) String instruction
    ) {
    }

    public record ParentOption(
            Long id,
            String planMonth,
            String taskName,
            String taskContent,
            String deliverable
    ) {
    }

    public record RelatedWeekItem(
            Long id,
            Long monthPlanItemId,
            String content,
            String deliverable,
            LocalDate plannedFinishDate
    ) {
    }

    public record ContextResponse(
            boolean enabled,
            String providerCode,
            String modelName,
            List<String> availableContext,
            List<String> missingContext,
            List<ParentOption> parentOptions,
            List<RelatedWeekItem> relatedWeekItems,
            Map<String, Integer> remainingCalls,
            String notice
    ) {
    }

    public record WeekDraft(
            String suggestionId,
            List<WeekItem> items,
            List<String> warnings,
            List<String> missingContext,
            String notice
    ) {
    }

    public record DayDraft(
            String suggestionId,
            Long relatedMonthPlanItemId,
            String content,
            String remark,
            List<String> warnings,
            List<String> missingContext,
            String notice
    ) {
    }
}
