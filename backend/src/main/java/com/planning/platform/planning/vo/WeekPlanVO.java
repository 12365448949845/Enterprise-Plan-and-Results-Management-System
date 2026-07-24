package com.planning.platform.planning.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class WeekPlanVO {

    private WeekPlanVO() {
    }

    public record ParentOptionVO(
            Long monthPlanItemId,
            Long monthPlanId,
            String monthPlanTitle,
            String planMonth,
            String taskType,
            BigDecimal performanceWeight,
            String taskName,
            LocalDate deadline,
            String status,
            Integer existingWeekPlanCount
    ) {
    }

    public record ItemVO(
            Long id,
            Long monthPlanItemId,
            String content,
            String deliverable,
            LocalDate plannedFinishDate,
            Integer sortNo,
            ParentOptionVO parent
    ) {
    }

    public record SummaryVO(
            Long id,
            String title,
            LocalDate weekStart,
            LocalDate weekEnd,
            String status,
            Integer versionNo,
            Long ownerUserId,
            String employeeName,
            Long deptId,
            String departmentName,
            Integer itemCount,
            LocalDateTime submitAt,
            LocalDateTime approveAt,
            String approvalComment
    ) {
    }

    public record DetailVO(
            SummaryVO summary,
            List<ItemVO> items,
            List<SummaryVO> siblingPlans,
            Long dayPlanCount
    ) {
    }

    public record ActionVO(Long id, String status, Integer versionNo, String message) {
    }
}
