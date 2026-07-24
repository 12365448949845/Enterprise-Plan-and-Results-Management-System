package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.PlanDraftAiModels;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanDraftAiOutputValidatorTest {

    private final AiOutputValidator validator = new AiOutputValidator(new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsValidWeekDraft() {
        String json = """
                {"items":[{"monthPlanItemId":11,"content":"完成接口联调",
                "deliverable":"联调记录","plannedFinishDate":"2026-07-31"}],"warnings":[]}
                """;

        PlanDraftAiModels.WeekDraft result = validator.validateWeekDraft(
                json, "AI-WEEK", LocalDate.parse("2026-07-27"), Set.of(11L), List.of());

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).monthPlanItemId()).isEqualTo(11L);
    }

    @Test
    void rejectsWeekParentOutsideAccessibleSet() {
        String json = """
                {"items":[{"monthPlanItemId":12,"content":"完成接口联调",
                "deliverable":"联调记录","plannedFinishDate":"2026-07-31"}],"warnings":[]}
                """;

        assertThatThrownBy(() -> validator.validateWeekDraft(
                json, "AI-WEEK", LocalDate.parse("2026-07-27"), Set.of(11L), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("月计划事项");
    }

    @Test
    void rejectsWeekItemOutsideSelectedWeek() {
        String json = """
                {"items":[{"monthPlanItemId":11,"content":"完成接口联调",
                "deliverable":"联调记录","plannedFinishDate":"2026-08-03"}],"warnings":[]}
                """;

        assertThatThrownBy(() -> validator.validateWeekDraft(
                json, "AI-WEEK", LocalDate.parse("2026-07-27"), Set.of(11L), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("自然周");
    }

    @Test
    void rejectsWeekDraftWithEmptyDeliverable() {
        String json = """
                {"items":[{"monthPlanItemId":11,"content":"完成接口联调",
                "deliverable":"","plannedFinishDate":"2026-07-31"}],"warnings":[]}
                """;

        assertThatThrownBy(() -> validator.validateWeekDraft(
                json, "AI-WEEK", LocalDate.parse("2026-07-27"), Set.of(11L), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("deliverable");
    }

    @Test
    void rejectsWeekDraftWithNumericOnlyContent() {
        String json = """
                {"items":[{"monthPlanItemId":11,"content":"123123...",
                "deliverable":"联调记录","plannedFinishDate":"2026-07-31"}],"warnings":[]}
                """;

        assertThatThrownBy(() -> validator.validateWeekDraft(
                json, "AI-WEEK", LocalDate.parse("2026-07-27"), Set.of(11L), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("工作描述");
    }

    @Test
    void acceptsValidDayDraft() {
        String json = """
                {"relatedMonthPlanItemId":11,"content":"上午完成接口开发，下午联调",
                "remark":"下班前整理问题清单","warnings":[]}
                """;

        PlanDraftAiModels.DayDraft result = validator.validateDayDraft(
                json, "AI-DAY", Set.of(11L), List.of());

        assertThat(result.relatedMonthPlanItemId()).isEqualTo(11L);
        assertThat(result.content()).contains("接口开发");
    }

    @Test
    void rejectsUnknownDayFields() {
        String json = """
                {"relatedMonthPlanItemId":11,"content":"完成接口开发","remark":"",
                "status":"APPROVED","warnings":[]}
                """;

        assertThatThrownBy(() -> validator.validateDayDraft(
                json, "AI-DAY", Set.of(11L), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("未授权字段");
    }
}
