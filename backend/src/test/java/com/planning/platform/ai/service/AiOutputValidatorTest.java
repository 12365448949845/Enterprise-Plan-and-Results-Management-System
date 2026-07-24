package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiModels;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutputValidatorTest {

    private final AiOutputValidator validator = new AiOutputValidator(new ObjectMapper());

    @Test
    void acceptsReducedMonthPlanFieldsAndIgnoresUnknownProperties() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"summary":"下月重点工作","items":[{
                  "workType":"PROJECT","taskName":"产品上线","taskContent":"完成版本上线",
                  "deliverable":"上线检查清单","deadline":"%s","performanceWeight":100,
                  "unexpectedId":999
                }],"warnings":[]}
                """.formatted(month.atEndOfMonth());

        AiModels.GenerateResponse result = validator.validateGenerate(json, "AI-1", month.toString(), List.of());

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).taskName()).isEqualTo("产品上线");
        assertThat(result.items().get(0).deliverable()).isEqualTo("上线检查清单");
    }

    @Test
    void rejectsDeadlineOutsideSelectedMonth() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"summary":"计划","items":[{"workType":"TASK","taskName":"任务","taskContent":"任务内容",
                "deliverable":"成果","deadline":"%s","performanceWeight":100}],"warnings":[]}
                """.formatted(month.plusMonths(1).atDay(1));

        assertThatThrownBy(() -> validator.validateGenerate(json, "AI-2", month.toString(), List.of()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("计划月份内");
    }

    @Test
    void rejectsCheckIssueThatTargetsArbitraryObjectPath() {
        String json = """
                {"issues":[{"code":"BAD","level":"HIGH","fieldPath":"ownerUserId",
                "message":"非法字段","suggestion":"不要修改"}]}
                """;
        assertThatThrownBy(() -> validator.validateCheck(json, "AI-3"))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("字段路径无效");
    }

    @Test
    void acceptsValidOptimizeOutput() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"item":{"workType":"PROJECT","taskName":"产品上线",
                "taskContent":"完成上线准备和发布","deliverable":"上线检查清单",
                "deadline":"%s","performanceWeight":20},"warnings":[]}
                """.formatted(month.atEndOfMonth());

        AiModels.OptimizeResponse result = validator.validateOptimize(json, "AI-4", month.toString());

        assertThat(result.item().workType()).isEqualTo("PROJECT");
        assertThat(result.item().taskName()).isEqualTo("产品上线");
        assertThat(result.item().performanceWeight()).isEqualByComparingTo("20");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsOptimizeOutputWithoutItemObject() {
        YearMonth month = YearMonth.now().plusMonths(1);

        assertThatThrownBy(() -> validator.validateOptimize("{\"warnings\":[]}", "AI-5", month.toString()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("item 必须为对象");
    }

    @Test
    void rejectsOptimizeWeightEncodedAsString() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"item":{"workType":"TASK","taskName":"任务","taskContent":"任务内容",
                "deliverable":"成果","deadline":"%s","performanceWeight":"20%%"},"warnings":[]}
                """.formatted(month.atEndOfMonth());

        assertThatThrownBy(() -> validator.validateOptimize(json, "AI-6", month.toString()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("performanceWeight 必须为数字");
    }

    @Test
    void rejectsOptimizeDeadlineOutsideSelectedMonth() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"item":{"workType":"TASK","taskName":"任务","taskContent":"任务内容",
                "deliverable":"成果","deadline":"%s","performanceWeight":20},"warnings":[]}
                """.formatted(month.plusMonths(1).atDay(1));

        assertThatThrownBy(() -> validator.validateOptimize(json, "AI-7", month.toString()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("计划月份内");
    }

    @Test
    void rejectsOptimizeWarningsThatAreNotAnArray() {
        YearMonth month = YearMonth.now().plusMonths(1);
        String json = """
                {"item":{"workType":"TASK","taskName":"任务","taskContent":"任务内容",
                "deliverable":"成果","deadline":"%s","performanceWeight":20},"warnings":"无"}
                """.formatted(month.atEndOfMonth());

        assertThatThrownBy(() -> validator.validateOptimize(json, "AI-8", month.toString()))
                .isInstanceOf(AiOutputValidator.OutputException.class)
                .hasMessageContaining("提示列表格式无效");
    }
}
