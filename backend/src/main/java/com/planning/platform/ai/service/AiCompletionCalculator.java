package com.planning.platform.ai.service;

import com.planning.platform.ai.model.AiReviewModels.AcceptanceCoverage;
import com.planning.platform.ai.model.AiReviewModels.Issue;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AiCompletionCalculator {

    public CompletionAssessment assess(Integer declaredRate, List<AcceptanceCoverage> coverage,
                                       List<Issue> baseIssues) {
        List<Issue> issues = new ArrayList<>(baseIssues == null ? List.of() : baseIssues);
        if (coverage == null || coverage.isEmpty()) {
            return new CompletionAssessment(null, null, "UNKNOWN", issues, null);
        }
        double minScore = 0D;
        double maxScore = 0D;
        boolean unproven = false;
        boolean anySupported = false;
        boolean anyUnknown = false;
        for (AcceptanceCoverage item : coverage) {
            switch (defaultText(item.status(), "UNKNOWN").toUpperCase(Locale.ROOT)) {
                case "PROVEN" -> {
                    minScore += 1D;
                    maxScore += 1D;
                    anySupported = true;
                }
                case "PARTIAL" -> {
                    minScore += 0.25D;
                    maxScore += 0.75D;
                    anySupported = true;
                }
                case "UNPROVEN" -> unproven = true;
                default -> {
                    maxScore += 1D;
                    anyUnknown = true;
                }
            }
        }
        int min = (int) Math.round(minScore * 100D / coverage.size());
        int max = (int) Math.round(maxScore * 100D / coverage.size());
        String evidenceStatus = minScore >= coverage.size() ? "SUFFICIENT"
                : anySupported ? "PARTIAL" : anyUnknown ? "UNKNOWN" : "INSUFFICIENT";
        if (declaredRate != null) {
            int above = declaredRate - max;
            int below = min - declaredRate;
            if (declaredRate == 100 && (unproven || anyUnknown)) {
                issues.add(issue("COMPLETION_RATE_UNSUPPORTED", "HIGH", "申报100%但仍有验收项未被证明",
                        "CALC_RESULT_01", declaredRate + "%",
                        "AI逐项对照结果中存在未证明或无法判断的验收项，因此100%的完成申报缺少完整证据支撑。",
                        "补充缺失证据，或根据实际覆盖情况调整完成比例。", 1D,
                        coverageReferences(coverage)));
            } else if (above > 25) {
                issues.add(rateIssue("HIGH", declaredRate, min, max, "完成比例明显高于证据覆盖范围"));
            } else if (above > 10) {
                issues.add(rateIssue("MEDIUM", declaredRate, min, max, "完成比例可能偏高"));
            } else if (below > 15) {
                issues.add(issue("COMPLETION_RATE_POSSIBLY_LOW", "LOW", "完成比例可能偏低",
                        "CALC_RESULT_03", declaredRate + "%",
                        "验收项证据覆盖情况对应的建议区间为" + min + "%～" + max + "%、高于当前申报。",
                        "核对是否遗漏已完成内容，或补充成果说明。", 0.85D, coverageReferences(coverage)));
            }
        }
        String calculationBasis = "按" + coverage.size()
                + "个验收项等权估算：已证明按100%，部分证明按25%～75%，未证明按0%，无法判断按0%～100%。";
        return new CompletionAssessment(min, max, evidenceStatus, issues, calculationBasis);
    }

    public CompletionAssessment withoutCompletion(List<Issue> issues) {
        return new CompletionAssessment(null, null, null, issues == null ? List.of() : issues, null);
    }

    private Issue rateIssue(String severity, int declared, int min, int max, String title) {
        return issue("COMPLETION_RATE_POSSIBLY_HIGH", severity, title,
                "CALC_RESULT_02", declared + "%",
                "验收项证据覆盖情况对应的建议区间为" + min + "%～" + max + "%、低于当前申报。",
                "核对完成比例，或补充能够覆盖未证明验收项的证据。", 0.9D,
                List.of("AI验收项覆盖明细", "员工申报完成比例"));
    }

    private Issue issue(String code, String severity, String title, String ruleId, String quote,
                        String basis, String suggestion, Double confidence, List<String> references) {
        return new Issue(code, "RULE", severity, "completionRate", title, ruleId, quote,
                basis, suggestion, confidence, references);
    }

    private List<String> coverageReferences(List<AcceptanceCoverage> coverage) {
        return coverage.stream()
                .filter(item -> item.evidenceReferences() != null)
                .flatMap(item -> item.evidenceReferences().stream())
                .distinct().limit(20).toList();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record CompletionAssessment(
            Integer suggestedMin,
            Integer suggestedMax,
            String evidenceStatus,
            List<Issue> issues,
            String calculationBasis
    ) {
    }
}
