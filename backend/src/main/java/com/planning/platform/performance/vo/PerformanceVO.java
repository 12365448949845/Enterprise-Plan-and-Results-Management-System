package com.planning.platform.performance.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PerformanceVO {

    private PerformanceVO() {
    }

    public record MetricVO(
            String code,
            String label,
            Integer value,
            String tone
    ) {
    }

    public record OrgNodeVO(
            Long id,
            String label,
            String orgType,
            List<OrgNodeVO> children
    ) {
    }

    public record ActionResultVO(
            String objectId,
            String status,
            String message,
            String auditActionCode,
            Boolean auditDeferred
    ) {
    }

    public record LeaderWorkbenchVO(
            List<OrgNodeVO> orgTree,
            List<MetricVO> metrics,
            List<LeaderDateStatusVO> dateStatuses,
            List<DailyReviewItemVO> subordinateSummaries
    ) {
    }

    public record LeaderDateStatusVO(
            LocalDate date,
            Long orgId,
            String orgName,
            Integer pendingReviewCount,
            Integer pendingSuggestCount,
            Integer overdueCount,
            String status
    ) {
    }

    public record DailyReviewItemVO(
            String id,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            LocalDate planDate,
            LocalDateTime submittedAt,
            String workContent,
            String deliverable,
            LocalDateTime approvalDueAt,
            Boolean overdueApproval,
            List<String> missingFields,
            String aiCheckResult,
            String reviewStatus,
            String riskLevel,
            String leaderComment,
            LocalDateTime reviewedAt
    ) {
    }

    public record EvidenceFileVO(
            Long fileId,
            String fileName,
            String evidenceType,
            String status,
            Boolean reviewPassed
    ) {
    }

    public record ResultSuggestionItemVO(
            String id,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            String resultNo,
            String resultTitle,
            String planType,
            Long planId,
            String planNo,
            BigDecimal completionRatio,
            String autoLevel,
            String evidenceStatus,
            List<String> issueCodes,
            String issueText,
            String suggestionStatus,
            String leaderSuggestion,
            String resultStatus,
            List<EvidenceFileVO> evidences
    ) {
    }

    public record PlanAdjustmentItemVO(
            String id,
            String originalPlanType,
            Long originalPlanId,
            String originalPlanNo,
            String originalWorkContent,
            String newPlanType,
            Long newPlanId,
            String newPlanNo,
            Long ownerId,
            String employeeName,
            String adjustmentType,
            String reason,
            String impactText,
            String operationComment,
            String status,
            Boolean keepEvidenceChain,
            String operatorName,
            LocalDateTime operatedAt
    ) {
    }

    public record ExtraMonthPlanApprovalVO(
            String id,
            Long monthPlanId,
            String planMonth,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            String taskName,
            String taskContent,
            String deliverable,
            LocalDate deadline,
            BigDecimal performanceWeight,
            String status,
            LocalDateTime submittedAt,
            Long approverId,
            LocalDateTime approvedAt,
            String approvalComment
    ) {
    }

    public record LedgerItemVO(
            String id,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer planCount,
            Integer resultCount,
            BigDecimal avgCompletionRatio,
            BigDecimal referenceScore,
            Integer overdueCount,
            Integer missingEvidenceCount,
            String evidenceChainStatus,
            String appealStatus
    ) {
    }

    public record DepartmentDashboardVO(
            List<MetricVO> metrics,
            List<DepartmentSummaryVO> summaries,
            List<TodoItemVO> urgentTodos
    ) {
    }

    public record DepartmentSummaryVO(
            Long orgId,
            String orgName,
            Integer monthPlanCount,
            Integer approvedPlanCount,
            Integer pendingPlanCount,
            Integer confirmedResultCount,
            BigDecimal closureRate,
            Integer missingFieldCount,
            Integer overdueCount,
            String riskSummary
    ) {
    }

    public record MonthPlanApprovalItemVO(
            String id,
            String planNo,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            Integer planYear,
            Integer planMonth,
            String workContent,
            String deliverable,
            LocalDate deadline,
            String status,
            String leaderComment,
            Long approverId,
            String approverName,
            LocalDateTime approvedAt,
            String aiCheckResult,
            List<String> missingFields,
            LocalDateTime submittedAt,
            Integer version,
            List<MonthPlanApprovalDetailItemVO> items
    ) {
    }

    public record MonthPlanApprovalDetailItemVO(
            Long id,
            String taskName,
            String taskContent,
            String deliverable,
            BigDecimal performanceWeight,
            LocalDate deadline,
            String status
    ) {
    }

    public record MonthPlanApprovalPageVO(
            List<MonthPlanApprovalItemVO> items,
            long total,
            int pageNo,
            int pageSize
    ) {
    }

    public record ResultConfirmItemVO(
            String id,
            String resultNo,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            String planType,
            Long planId,
            String planNo,
            String resultTitle,
            BigDecimal completionRatio,
            String autoLevel,
            String evidenceStatus,
            String leaderSuggestion,
            List<String> issueCodes,
            String issueText,
            String confirmStatus,
            List<EvidenceFileVO> evidences
    ) {
    }

    public record TodoItemVO(
            String id,
            String sceneCode,
            String title,
            String triggerText,
            Long receiverId,
            String receiverName,
            String objectType,
            String objectId,
            LocalDateTime dueAt,
            String requirement,
            String impact,
            String status,
            Integer remindCount,
            String routeHint
    ) {
    }

    public record DepartmentDayPlanReviewVO(
            String id,
            Long ownerId,
            String employeeNo,
            String employeeName,
            Long orgId,
            String orgName,
            LocalDate planDate,
            LocalDateTime submittedAt,
            String workContent,
            String deliverable,
            LocalDateTime approvalDueAt,
            Boolean overdueApproval,
            List<String> missingFields,
            String aiCheckResult,
            String reviewStatus,
            String riskLevel,
            String leaderComment,
            String leaderName,
            LocalDateTime reviewedAt,
            String status,
            String departmentComment,
            String departmentReviewerName,
            LocalDateTime departmentReviewedAt
    ) {
    }

    public record AppealProcessVO(
            Long id,
            String appealNo,
            String title,
            String reason,
            String status,
            Long ownerUserId,
            String employeeName,
            String orgName,
            Long relatedResultId,
            String resultTitle,
            String resultStatus,
            Integer completionRate,
            Long handlerId,
            String handleComment,
            LocalDateTime createdAt,
            LocalDateTime handledAt
    ) {
    }

    public record DeliverableTemplateVO(
            Long id,
            Long orgId,
            String orgName,
            String templateName,
            String evidenceType,
            Boolean required,
            String appliesTo,
            String description,
            String versionNo,
            String status,
            Integer referenceCount
    ) {
    }

    public record AcceptanceStandardVO(
            Long id,
            Long templateId,
            String templateName,
            String standardText,
            Boolean requireReviewPassed,
            String evidenceRequirement,
            String versionNo,
            String status
    ) {
    }

    public record ScoreRuleVO(
            Long id,
            Long orgId,
            String orgName,
            String ruleName,
            String status,
            LocalDate effectiveStart,
            LocalDate effectiveEnd,
            Map<String, Object> ruleJson
    ) {
    }

    public record ScoreSimulationVO(
            String employeeName,
            BigDecimal score,
            List<String> hitFactors,
            String explanation
    ) {
    }

    public record ExportTaskVO(
            String id,
            String dimensionType,
            String dimensionName,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<String> formats,
            Boolean includeEvidence,
            String watermark,
            String integrityStatus,
            List<String> missingItems,
            String checksum,
            String status,
            String sizeText,
            Long requestedBy,
            String requestedByName,
            LocalDateTime requestedAt,
            LocalDateTime finishedAt,
            LocalDateTime expireAt,
            String errorMessage
    ) {
    }

    public record ExportDownloadVO(
            String taskId,
            String status,
            String fileName,
            String downloadUrl,
            LocalDateTime expireAt,
            String checksum
    ) {
    }
}
