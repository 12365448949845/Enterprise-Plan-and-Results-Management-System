package com.planning.platform.ai.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AiReviewModels {

    private AiReviewModels() {
    }

    public record Issue(
            String code,
            String source,
            String severity,
            String field,
            String title,
            String ruleId,
            String quote,
            String basis,
            String suggestion,
            Double confidence,
            List<String> references
    ) {
    }

    public record AcceptanceCoverage(
            String criterionId,
            String criterion,
            String status,
            String basis,
            String evidenceQuote,
            Double confidence,
            List<String> evidenceReferences
    ) {
    }

    public record AnalysisDimension(
            String ruleId,
            String title,
            String status,
            String conclusion,
            String quote,
            String basis,
            Double confidence,
            List<String> references
    ) {
    }

    public record SourceReference(
            String id,
            String path,
            String label,
            String content
    ) {
    }

    public record ReviewResult(
            String overallRisk,
            String summary,
            List<Issue> issues,
            List<AnalysisDimension> analysisDimensions,
            List<AcceptanceCoverage> acceptanceCoverage,
            Integer suggestedCompletionMin,
            Integer suggestedCompletionMax,
            String evidenceStatus,
            Integer declaredCompletionRate,
            String completionCalculationBasis
    ) {
    }

    public record ReviewVO(
            Long id,
            String bizType,
            Long bizId,
            String bizVersion,
            String contentHash,
            String status,
            String overallRisk,
            String provider,
            String modelName,
            String promptVersion,
            LocalDateTime checkedAt,
            boolean stale,
            boolean modelEnabled,
            String errorMessage,
            ReviewResult result
    ) {
    }

    public record CapabilityVO(
            boolean modelEnabled,
            String mode,
            String provider,
            String modelName,
            String promptVersion,
            String message
    ) {
    }

    public record AnalysisRequest(
            String bizType,
            List<Map<String, Object>> rules,
            Map<String, Object> businessData,
            List<SourceReference> sourceCatalog,
            Map<String, List<String>> requiredReferences
    ) {
        public AnalysisRequest(String bizType, List<Map<String, Object>> rules,
                               Map<String, Object> businessData, List<SourceReference> sourceCatalog) {
            this(bizType, rules, businessData, sourceCatalog, Map.of());
        }
    }

    public record AnalysisCallContext(
            Long userId,
            Long orgId,
            Long bizId,
            String contentHash
    ) {
    }

    public record ModelAnalysis(
            String summary,
            List<Issue> issues,
            List<AnalysisDimension> analysisDimensions,
            List<AcceptanceCoverage> acceptanceCoverage
    ) {
    }
}
