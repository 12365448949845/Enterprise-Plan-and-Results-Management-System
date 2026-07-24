package com.planning.platform.planning.vo;

public record PlanningStatsRespVO(
        Long pendingDayPlans,
        Long overduePendingDayPlans,
        Long currentMonthResults,
        String closureRate
) {
}
