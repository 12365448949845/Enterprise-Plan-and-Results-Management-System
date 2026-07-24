package com.planning.platform.ai.service;

import com.planning.platform.ai.model.AiReviewModels.AcceptanceCoverage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCompletionCalculatorTest {

    private final AiCompletionCalculator calculator = new AiCompletionCalculator();

    @Test
    void calculatesTransparentRangeFromAcceptanceCoverage() {
        var result = calculator.assess(70, List.of(
                coverage("接口已实现", "PROVEN", "测试报告第2页"),
                coverage("结果已回传", "PROVEN", "测试报告第4页"),
                coverage("操作记录完整", "UNPROVEN", null)
        ), List.of());

        assertThat(result.suggestedMin()).isEqualTo(67);
        assertThat(result.suggestedMax()).isEqualTo(67);
        assertThat(result.evidenceStatus()).isEqualTo("PARTIAL");
        assertThat(result.calculationBasis()).contains("3个验收项等权估算");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void flagsOneHundredPercentWhenAnyCriterionIsNotProven() {
        var result = calculator.assess(100, List.of(
                coverage("功能完成", "PROVEN", "报告第1页"),
                coverage("评审通过", "UNKNOWN", null)
        ), List.of());

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("COMPLETION_RATE_UNSUPPORTED");
        assertThat(result.issues().get(0).basis()).contains("未证明或无法判断");
    }

    @Test
    void returnsUnknownWhenNoCoverageCanBeEstablished() {
        var result = calculator.assess(80, List.of(), List.of());

        assertThat(result.suggestedMin()).isNull();
        assertThat(result.suggestedMax()).isNull();
        assertThat(result.evidenceStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void unknownCriterionExpandsRangeInsteadOfPretendingItIsIncomplete() {
        var result = calculator.assess(70, List.of(
                coverage("接口已实现", "PROVEN", "报告第1页"),
                coverage("安全评审通过", "UNKNOWN", null)
        ), List.of());

        assertThat(result.suggestedMin()).isEqualTo(50);
        assertThat(result.suggestedMax()).isEqualTo(100);
        assertThat(result.evidenceStatus()).isEqualTo("PARTIAL");
    }

    private AcceptanceCoverage coverage(String criterion, String status, String reference) {
        return new AcceptanceCoverage("ACCEPTANCE_1", criterion, status, "依据", "证据原文", 0.9,
                reference == null ? List.of() : List.of(reference));
    }
}
