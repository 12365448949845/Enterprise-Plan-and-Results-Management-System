package com.planning.platform.dispute.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DisputeVO {
    private DisputeVO() {}

    public record DashboardVO(List<MetricVO> metrics, List<CaseItemVO> recentCases) {}
    public record MetricVO(String code, String label, Integer value, String tone) {}
    public record CaseItemVO(
            Long id, String caseNo, Long appealId, String employeeName, String orgName,
            LocalDate periodStart, LocalDate periodEnd, String disputeSubject, String appealTitle,
            String status, String packageStatus, LocalDateTime deadlineAt, Integer reviewerCount,
            Integer opinionCount
    ) {}
    public record DetailVO(
            CaseItemVO summary, String appealReason, String appealStatus, Long relatedResultId,
            String resultTitle, String resultStatus, List<String> packageItems,
            List<ReviewerVO> reviewers, List<OpinionVO> opinions,
            boolean canDecide, String decision, String decisionComment
    ) {}
    public record ReviewerVO(
            Long id, Long userId, String userName, String sourceType,
            String recusalStatus, String recusalReason, boolean currentUser
    ) {}
    public record ReviewerCandidateVO(Long userId, String employeeNo, String userName, Long deptId) {}
    public record OpinionVO(
            Long id, Long reviewerId, String reviewerName, String opinion,
            String comment, Integer versionNo, LocalDateTime submittedAt
    ) {}
}
