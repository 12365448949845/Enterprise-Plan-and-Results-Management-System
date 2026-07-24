package com.planning.platform.ai.service;

import com.planning.platform.ai.model.AiReviewModels.AcceptanceCoverage;
import com.planning.platform.ai.model.AiReviewModels.AnalysisDimension;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.Issue;
import com.planning.platform.ai.model.AiReviewModels.ModelAnalysis;
import com.planning.platform.ai.model.AiReviewModels.SourceReference;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiGroundingServiceTest {

    private final AiGroundingService service = new AiGroundingService();

    @Test
    void keepsOnlyIssueWithWhitelistedRuleAndExactSourceQuote() {
        AnalysisRequest request = service.prepare("DAY_PLAN", rules("SEM_DAY_01"), new LinkedHashMap<>(Map.of(
                "dayPlan", Map.of("content", "完成AI接口联调并核对风险提示"),
                "instruction", "这不是可引用的业务事实"
        )));
        SourceReference source = sourceAt(request, "businessData.dayPlan.content");
        Issue valid = issue("SEM_DAY_01", "完成AI接口联调", source.id());
        Issue fakeRule = issue("SEM_FAKE_01", "完成AI接口联调", source.id());
        Issue fakeQuote = issue("SEM_DAY_01", "完成不存在的财务审批", source.id());

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("模型总结", List.of(valid, fakeRule, fakeQuote), List.of(), List.of()));

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).ruleId()).isEqualTo("SEM_DAY_01");
        assertThat(result.issues().get(0).field()).isEqualTo("businessData.dayPlan.content");
        assertThat(result.issues().get(0).references()).containsExactly("当前日计划 · 任务内容");
        assertThat(request.sourceCatalog()).noneMatch(item -> item.path().endsWith(".instruction"));
        assertThat(result.summary()).contains("通过来源原文校验");
        assertThat(result.analysisDimensions()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("UNKNOWN"));
    }

    @Test
    void rejectsIssueWhenAnySourceIdWasInvented() {
        AnalysisRequest request = service.prepare("DAY_PLAN", rules("SEM_DAY_01"), Map.of(
                "dayPlan", Map.of("content", "完成接口联调")
        ));
        SourceReference source = sourceAt(request, "businessData.dayPlan.content");
        Issue issue = new Issue("AI_RISK", "AI", "MEDIUM", "dayPlan.content", "风险",
                "SEM_DAY_01", "完成接口联调", "判断依据", "修改建议", 0.8,
                List.of(source.id(), "SRC_9999"));

        ModelAnalysis result = service.validate(request, new ModelAnalysis("", List.of(issue), List.of(), List.of()));

        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsIssueThatCitesARealButUnrelatedSource() {
        AnalysisRequest request = service.prepare("DAY_PLAN", rules("SEM_DAY_01"), Map.of(
                "dayPlan", Map.of("content", "完成接口联调"),
                "workdayRule", Map.of("description", "默认工作日")
        ));
        SourceReference unrelated = sourceAt(request, "businessData.workdayRule.description");
        Issue issue = issue("SEM_DAY_01", "默认工作日", unrelated.id());

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(issue), List.of(), List.of()));

        assertThat(result.issues()).isEmpty();
    }

    @Test
    void calibratesRiskSeverityFromServerRuleInsteadOfModelSelfRating() {
        AnalysisRequest request = service.prepare("MONTH_PLAN", List.of(
                Map.of("id", "SEM_PLAN_01", "text", "任务是否具体"),
                Map.of("id", "SEM_PLAN_03", "text", "范围与期限是否合理")
        ), Map.of("items", List.of(Map.of("taskContent", "优化系统"))));
        SourceReference source = sourceAt(request, "businessData.items[0].taskContent");
        Issue modelHigh = new Issue("VAGUE", "AI", "HIGH", source.path(), "内容笼统",
                "SEM_PLAN_01", source.content(), "缺少具体行动。", "补充行动。", 0.9,
                List.of(source.id()));
        Issue modelLow = new Issue("SCOPE", "AI", "LOW", source.path(), "范围不明",
                "SEM_PLAN_03", source.content(), "无法确认范围与期限。", "补充范围。", 0.9,
                List.of(source.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(modelHigh, modelLow), List.of(), List.of()));

        assertThat(result.issues()).extracting(Issue::severity)
                .containsExactly("MEDIUM", "HIGH");
    }

    @Test
    void validatesEvidenceQuoteAndFillsMissingCriterionAsUnknown() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("acceptanceCriteria", List.of(
                Map.of("id", "AC_1", "text", "接口能够正常返回结果"),
                Map.of("id", "AC_2", "text", "操作日志完整")
        ));
        data.put("evidence", Map.of("extractedText", """
                [证据:测试报告.pdf 第1页]
                接口测试共执行20次，全部正常返回结果。
                [证据:测试报告.pdf 第2页]
                未提供操作日志截图。
                """));
        AnalysisRequest request = service.prepare("RESULT", rules("SEM_RESULT_02"), data);
        SourceReference firstPage = request.sourceCatalog().stream()
                .filter(item -> item.label().contains("第1页"))
                .findFirst().orElseThrow();
        AcceptanceCoverage valid = new AcceptanceCoverage("AC_1", "模型改写的验收项", "PROVEN",
                "测试报告直接记录了接口测试结果。", "接口测试共执行20次，全部正常返回结果。", 0.9,
                List.of(firstPage.id()));

        ModelAnalysis result = service.validate(request, new ModelAnalysis("", List.of(), List.of(), List.of(valid)));

        assertThat(result.acceptanceCoverage()).hasSize(2);
        assertThat(result.acceptanceCoverage().get(0).criterion()).isEqualTo("接口能够正常返回结果");
        assertThat(result.acceptanceCoverage().get(0).evidenceReferences())
                .containsExactly("[证据:测试报告.pdf 第1页]");
        assertThat(result.acceptanceCoverage().get(1).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void verifiedUnprovenCoverageCanBackTheAcceptanceCoverageRiskDimension() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("acceptanceCriteria", List.of(Map.of("id", "AC_1", "text", "应提供上线报告")));
        data.put("evidence", Map.of("extractedText", "[证据:材料.pdf 第1页]\n仅有项目标题"));
        AnalysisRequest request = service.prepare("RESULT", rules("SEM_RESULT_02"), data);
        SourceReference criterion = sourceAt(request, "businessData.acceptanceCriteria[0].text");
        SourceReference evidence = request.sourceCatalog().stream()
                .filter(item -> item.path().startsWith("businessData.evidence.extractedText#"))
                .findFirst().orElseThrow();
        AcceptanceCoverage coverage = new AcceptanceCoverage("AC_1", "应提供上线报告", "UNPROVEN",
                "证据未包含上线报告。", "仅有项目标题", 0.9, List.of(evidence.id()));
        AnalysisDimension dimension = new AnalysisDimension(
                "SEM_RESULT_02", "验收项证据覆盖", "RISK", "验收项未被证明。",
                "仅有项目标题", "证据没有覆盖验收要求。", 0.9,
                List.of(criterion.id(), evidence.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(dimension), List.of(coverage)));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("RISK");
            assertThat(item.references()).containsExactly("[证据:材料.pdf 第1页]");
            assertThat(item.basis()).contains("1项未证明");
        });
    }

    @Test
    void downgradesCoverageWhenEvidenceQuoteIsFabricatedOrCitesPlanField() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("acceptanceCriteria", List.of(Map.of("id", "AC_1", "text", "完成评审")));
        data.put("planItem", Map.of("target", "完成评审"));
        data.put("evidence", Map.of("extractedText", "[证据:报告.docx 段落1]\n材料已提交。"));
        AnalysisRequest request = service.prepare("RESULT", rules("SEM_RESULT_02"), data);
        SourceReference planTarget = sourceAt(request, "businessData.planItem.target");
        AcceptanceCoverage invalid = new AcceptanceCoverage("AC_1", "完成评审", "PROVEN",
                "计划目标写了完成评审。", "完成评审", 0.9, List.of(planTarget.id()));

        ModelAnalysis result = service.validate(request, new ModelAnalysis("", List.of(), List.of(), List.of(invalid)));

        assertThat(result.acceptanceCoverage()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("UNKNOWN");
                    assertThat(item.basis()).contains("真实性校验");
                });
    }

    @Test
    void returnsOneGroundedDimensionForEveryConfiguredRule() {
        AnalysisRequest request = service.prepare("DAY_PLAN", List.of(
                Map.of("id", "SEM_DAY_01", "text", "行动与结果是否具体"),
                Map.of("id", "SEM_DAY_02", "text", "是否支撑上级计划")
        ), Map.of("dayPlan", Map.of("content", "完成AI接口联调并输出测试记录")));
        SourceReference source = sourceAt(request, "businessData.dayPlan.content");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_DAY_01", "行动与结果", "PASS", "任务包含明确行动和输出。",
                "完成AI接口联调并输出测试记录", "原文同时说明了行动对象和预期输出。",
                0.9, List.of(source.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).hasSize(2);
        assertThat(result.analysisDimensions().get(0).status()).isEqualTo("PASS");
        assertThat(result.analysisDimensions().get(0).references())
                .containsExactly("当前日计划 · 任务内容");
        assertThat(result.analysisDimensions().get(1).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void downgradesPassWhenAnotherDimensionFoundRiskInTheSameRequiredField() {
        AnalysisRequest request = service.prepare("DAY_PLAN", List.of(
                Map.of("id", "SEM_DAY_01", "text", "行动是否具体"),
                Map.of("id", "SEM_DAY_03", "text", "范围是否适合一天")
        ), Map.of("dayPlan", Map.of("content", "优化系统")));
        SourceReference source = sourceAt(request, "businessData.dayPlan.content");
        Issue vague = issue("SEM_DAY_01", "优化系统", source.id());
        AnalysisDimension vagueRisk = new AnalysisDimension(
                "SEM_DAY_01", "行动具体性", "RISK", "内容过于笼统。",
                "优化系统", "没有说明具体行动和对象。", 0.9, List.of(source.id()));
        AnalysisDimension scopePass = new AnalysisDimension(
                "SEM_DAY_03", "工作范围", "PASS", "适合一天完成。",
                "优化系统", "任务范围较小。", 0.9, List.of(source.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(vague), List.of(vagueRisk, scopePass), List.of()));

        assertThat(result.analysisDimensions()).extracting(AnalysisDimension::status)
                .containsExactly("RISK", "UNKNOWN");
        assertThat(result.analysisDimensions().get(1).basis()).contains("其他检查项中发现实质风险");
    }

    @Test
    void duplicateExtraTaskRiskDoesNotInvalidateIndependentContentSpecificityPass() {
        AnalysisRequest request = service.prepare("EXTRA_TASK", List.of(
                Map.of("id", "SEM_PLAN_01", "text", "内容是否具体"),
                Map.of("id", "SEM_EXTRA_01", "text", "是否属于新增任务")
        ), Map.of(
                "extraTask", Map.of("taskContent", "完成需求分析并输出评审记录"),
                "existingMonthTasks", List.of(Map.of("taskContent", "完成需求分析并输出评审记录"))
        ));
        SourceReference current = sourceAt(request, "businessData.extraTask.taskContent");
        SourceReference existing = sourceAt(request, "businessData.existingMonthTasks[0].taskContent");
        Issue duplicate = new Issue("DUPLICATE", "AI", "HIGH", current.path(), "任务重复",
                "SEM_EXTRA_01", current.content(), "与原计划任务相同。", "核对新增性。", 0.9,
                List.of(current.id(), existing.id()));
        AnalysisDimension contentPass = new AnalysisDimension(
                "SEM_PLAN_01", "任务内容具体性", "PASS", "行动和结果明确。",
                current.content(), "说明了行动和交付结果。", 0.9, List.of(current.id()));
        AnalysisDimension duplicateRisk = new AnalysisDimension(
                "SEM_EXTRA_01", "额外任务新增性", "RISK", "与原任务重复。",
                current.content(), "当前任务和原任务内容相同。", 0.9,
                List.of(current.id(), existing.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(duplicate), List.of(contentPass, duplicateRisk), List.of()));

        assertThat(result.analysisDimensions()).extracting(AnalysisDimension::status)
                .containsExactly("PASS", "RISK");
    }

    @Test
    void downgradesPlanPassWhenModelOnlyCheckedOneOfSeveralTasks() {
        AnalysisRequest request = service.prepare("MONTH_PLAN", List.of(
                Map.of("id", "SEM_PLAN_01", "text", "任务内容是否具体")
        ), Map.of("items", List.of(
                Map.of("taskContent", "开发客户导入接口并输出测试记录"),
                Map.of("taskContent", "优化系统")
        )));
        SourceReference first = sourceAt(request, "businessData.items[0].taskContent");
        AnalysisDimension sampledPass = new AnalysisDimension(
                "SEM_PLAN_01", "任务具体性", "PASS", "所有任务均具体。",
                "开发客户导入接口并输出测试记录", "第一项任务描述具体。", 0.9,
                List.of(first.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(sampledPass), List.of()));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("UNKNOWN");
            assertThat(item.basis()).contains("只核对了部分计划内容");
        });
    }

    @Test
    void createsReadableLabelsForIndexedPlanFields() {
        AnalysisRequest request = service.prepare("MONTH_PLAN", rules("SEM_PLAN_01"), Map.of(
                "items", List.of(Map.of("taskContent", "完成接口联调"))
        ));

        SourceReference source = sourceAt(request, "businessData.items[0].taskContent");

        assertThat(source.label()).isEqualTo("当前计划第1项 · 任务内容");
    }

    @Test
    void downgradesWeekRepeatPassWhenNoComparisonContentExists() {
        AnalysisRequest request = service.prepare("WEEK_PLAN", rules("SEM_WEEK_04"), Map.of(
                "itemsWithParents", List.of(Map.of(
                        "weekItem", Map.of("content", "完成接口联调并输出测试记录")
                )),
                "otherWeeksForSameMonthItems", List.of()
        ));
        SourceReference current = sourceAt(request,
                "businessData.itemsWithParents[0].weekItem.content");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_WEEK_04", "非机械重复检查", "PASS", "不存在机械重复。",
                "完成接口联调并输出测试记录", "当前内容与其他周不同。", 0.9,
                List.of(current.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("UNKNOWN");
            assertThat(item.basis()).contains("未覆盖本维度所需的全部关键字段");
        });
    }

    @Test
    void keepsWeekRepeatPassWhenCurrentAndComparisonContentsAreCited() {
        AnalysisRequest request = service.prepare("WEEK_PLAN", rules("SEM_WEEK_04"), Map.of(
                "itemsWithParents", List.of(Map.of(
                        "weekItem", Map.of("content", "完成接口联调并输出测试记录")
                )),
                "otherWeeksForSameMonthItems", List.of(Map.of(
                        "items", List.of(Map.of("content", "完成需求评审并输出问题清单"))
                ))
        ));
        SourceReference current = sourceAt(request,
                "businessData.itemsWithParents[0].weekItem.content");
        SourceReference comparison = sourceAt(request,
                "businessData.otherWeeksForSameMonthItems[0].items[0].content");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_WEEK_04", "非机械重复检查", "PASS", "两周任务具有明确阶段差异。",
                "完成接口联调并输出测试记录", "当前周是联调，上一周是需求评审。", 0.9,
                List.of(current.id(), comparison.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("PASS");
            assertThat(item.references()).containsExactly(
                    "第1项周计划 · 任务内容",
                    "对比周计划1 · 第1项 · 任务内容");
        });
    }

    @Test
    void downgradesDayRepeatPassWhenNoRecentDayPlanExists() {
        AnalysisRequest request = service.prepare("DAY_PLAN", rules("SEM_DAY_04"), Map.of(
                "dayPlan", Map.of("content", "完成接口联调并输出测试记录"),
                "recentDayPlans", List.of()
        ));
        SourceReference current = sourceAt(request, "businessData.dayPlan.content");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_DAY_04", "连续重复风险", "PASS", "不存在连续复制。",
                "完成接口联调并输出测试记录", "近期内容均不相同。", 0.9,
                List.of(current.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("UNKNOWN"));
    }

    @Test
    void fillsPassQuoteFromVerifiedSourcesWhenModelOmitsIt() {
        AnalysisRequest request = service.prepare("MONTH_PLAN", rules("SEM_PLAN_04"), Map.of(
                "items", List.of(
                        Map.of("taskContent", "开发客户导入接口并完成联调"),
                        Map.of("taskContent", "实现经营报表并核对数据")
                )
        ));
        SourceReference first = sourceAt(request, "businessData.items[0].taskContent");
        SourceReference second = sourceAt(request, "businessData.items[1].taskContent");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_PLAN_04", "任务重复冲突", "PASS", "两项任务目标不同。",
                "", "一项为接口开发，一项为报表开发。", 0.9,
                List.of(first.id(), second.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("PASS");
            assertThat(item.quote()).isEqualTo("开发客户导入接口并完成联调");
        });
    }

    @Test
    void repairsScalarSourceQuoteWrappedAsJsonFragment() {
        AnalysisRequest request = service.prepare("EXTRA_TASK", rules("SEM_EXTRA_02"), Map.of(
                "extraTask", Map.of("performanceWeight", "10.00"),
                "existingMonthTasks", List.of(Map.of("performanceWeight", "0.00"))
        ));
        SourceReference weight = sourceAt(request, "businessData.extraTask.performanceWeight");
        SourceReference originalWeight = sourceAt(request,
                "businessData.existingMonthTasks[0].performanceWeight");
        Issue issue = new Issue("WEIGHT_CONFLICT", "AI", "MEDIUM", "extraTask.performanceWeight",
                "权重冲突", "SEM_EXTRA_02", "绩效权重\":\"10.00", "权重与原任务冲突。",
                "核对权重分配。", 0.9, List.of(weight.id(), originalWeight.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(issue), List.of(), List.of()));

        assertThat(result.issues()).singleElement()
                .satisfies(item -> assertThat(item.quote()).isEqualTo("10.00"));
    }

    @Test
    void acceptsModelQuoteFromRedactedSourceText() {
        AnalysisRequest request = service.prepare("DAY_PLAN", rules("SEM_DAY_01"), Map.of(
                "dayPlan", Map.of("content", "联系13812345678完成客户联调")
        ));
        SourceReference source = sourceAt(request, "businessData.dayPlan.content");
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_DAY_01", "行动与结果具体性", "PASS", "任务内容具体。",
                "联系138****5678完成客户联调", "包含明确行动和对象。", 0.9,
                List.of(source.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("PASS");
            assertThat(item.quote()).doesNotContain("13812345678").contains("138****5678");
        });
    }

    @Test
    void redactsSensitiveDataFromDisplayedSourceLabels() {
        SourceReference source = new SourceReference("SRC_0001", "businessData.dayPlan.content",
                "附件13812345678.pdf", "完成客户联调");
        AnalysisRequest request = new AnalysisRequest("DAY_PLAN", rules("SEM_DAY_01"), Map.of(),
                List.of(source), Map.of("SEM_DAY_01", List.of(source.id())));
        AnalysisDimension pass = new AnalysisDimension(
                "SEM_DAY_01", "行动与结果具体性", "PASS", "任务内容具体。",
                "完成客户联调", "包含明确行动。", 0.9, List.of(source.id()));

        ModelAnalysis result = service.validate(request,
                new ModelAnalysis("", List.of(), List.of(pass), List.of()));

        assertThat(result.analysisDimensions()).singleElement()
                .satisfies(item -> assertThat(item.references())
                        .containsExactly("附件138****5678.pdf"));
    }

    private List<Map<String, Object>> rules(String id) {
        return List.of(Map.of("id", id, "text", "检查规则"));
    }

    private Issue issue(String ruleId, String quote, String sourceId) {
        return new Issue("AI_RISK", "AI", "MEDIUM", "dayPlan.content", "风险",
                ruleId, quote, "判断依据", "修改建议", 0.8, List.of(sourceId));
    }

    private SourceReference sourceAt(AnalysisRequest request, String path) {
        return request.sourceCatalog().stream().filter(item -> path.equals(item.path()))
                .findFirst().orElseThrow();
    }
}
