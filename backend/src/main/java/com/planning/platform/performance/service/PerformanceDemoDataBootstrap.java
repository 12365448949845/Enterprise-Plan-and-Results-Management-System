package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.performance.domain.BizAcceptanceStandard;
import com.planning.platform.performance.domain.BizDeliverableTemplate;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import com.planning.platform.performance.domain.BizScoreRule;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.mapper.BizScoreRuleMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

@Component
@Order(2)
@ConditionalOnProperty(prefix = "planning.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PerformanceDemoDataBootstrap implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper resultEvidenceMapper;
    private final BizPlanAdjustmentMapper adjustmentMapper;
    private final BizTodoMapper todoMapper;
    private final BizDeliverableTemplateMapper templateMapper;
    private final BizAcceptanceStandardMapper standardMapper;
    private final BizScoreRuleMapper scoreRuleMapper;
    private final PerformanceJsonCodec jsonCodec;

    @Value("${planning.storage.upload-root:uploads/employee-results}")
    private String uploadRootPath;

    @Override
    public void run(String... args) throws Exception {
        SysUser employee = findUser("employee");
        SysUser employeeTwo = findUser("employee2");
        SysUser leader = findUser("leader");
        SysUser departmentOwner = findUser("dept.owner");
        if (employee == null || employeeTwo == null || leader == null || departmentOwner == null) {
            return;
        }

        YearMonth currentMonth = YearMonth.now();
        BizMonthPlan pendingPlan = ensureMonthPlan(employee, currentMonth, "本月重点产品需求与版本交付", "PENDING", 1);
        pendingPlan.setApproverId(leader.getId());
        monthPlanMapper.updateById(pendingPlan);
        BizMonthPlanItem pendingItem = ensureMonthPlanItem(pendingPlan, "重点需求交付", "完成需求分析、设计评审与版本验收",
                "完成本月重点需求上线", "需求说明、评审记录、上线报告", "评审通过且上线验证无阻断问题",
                new BigDecimal("72.0"), currentMonth.atEndOfMonth());

        BizMonthPlan approvedPlan = ensureMonthPlan(employeeTwo, currentMonth, "客户交付专项计划", "APPROVED", 1);
        BizMonthPlanItem approvedItem = ensureMonthPlanItem(approvedPlan, "客户交付专项", "完成客户环境部署和验收",
                "完成客户正式验收", "部署文档、验收报告", "客户签字确认且遗留问题已登记",
                new BigDecimal("64.0"), currentMonth.atEndOfMonth().minusDays(2));

        ensureDayPlan(employee, pendingPlan, pendingItem, LocalDate.now(), "完成重点需求接口联调并整理评审材料", "PENDING_COMMENT");
        ensureDayPlan(employeeTwo, approvedPlan, approvedItem, LocalDate.now().minusDays(1), "完成客户环境部署检查和问题清单", "RISK_MARKED");

        BizResult pendingSuggestion = ensureResult(employee, pendingPlan, pendingItem, 86, "PENDING_SUGGEST", "PENDING");
        ensureEvidence(pendingSuggestion, employee, "重点需求阶段成果.pdf");
        BizResult pendingConfirm = ensureResult(employeeTwo, approvedPlan, approvedItem, 100, "SUGGEST_CONFIRM", "PENDING");
        pendingConfirm.setLeaderSuggestion("证据完整，建议确认");
        pendingConfirm.setSuggestedBy(leader.getId());
        pendingConfirm.setSuggestedAt(LocalDateTime.now().minusHours(2));
        resultMapper.updateById(pendingConfirm);
        ensureEvidence(pendingConfirm, employeeTwo, "客户验收成果.pdf");

        ensureAdjustment(employeeTwo, approvedPlan);
        ensureTodo("MONTH_PLAN_APPROVAL", "月计划待审批", employee.getRealName() + "提交了月计划", leader,
                "MONTH_PLAN", String.valueOf(pendingPlan.getId()), "/leader/month-plan-approval", employee.getDeptId());
        ensureTodo("DAY_PLAN_REVIEW", "日计划待点评", employee.getRealName() + "提交了日计划", leader,
                "DAY_PLAN", String.valueOf(findDayPlan(employee.getId(), LocalDate.now()).getId()), "/leader/daily-review", employee.getDeptId());
        ensureTodo("RESULT_SUGGEST", "成果待建议", employee.getRealName() + "提交了成果", leader,
                "RESULT", String.valueOf(pendingSuggestion.getId()), "/leader/result-suggest", employee.getDeptId());
        ensureTodo("RESULT_CONFIRM", "成果最终确认", leader.getRealName() + "提交了成果确认建议", departmentOwner,
                "RESULT", String.valueOf(pendingConfirm.getId()), "/department/result-confirm", employeeTwo.getDeptId());

        ensureConfiguration(departmentOwner);
    }

    private SysUser findUser(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private BizMonthPlan ensureMonthPlan(SysUser owner, YearMonth month, String content, String status, int version) {
        BizMonthPlan existing = monthPlanMapper.selectOne(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, owner.getId())
                .eq(BizMonthPlan::getPlanMonth, month.toString())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        BizMonthPlan plan = new BizMonthPlan();
        plan.setTitle(content);
        plan.setPlanMonth(month.toString());
        plan.setContent(content);
        plan.setOwnerUserId(owner.getId());
        plan.setDeptId(owner.getDeptId());
        plan.setStatus(status);
        plan.setVersionNo(version);
        plan.setSubmitAt(LocalDateTime.now().minusDays(2));
        if ("APPROVED".equals(status)) {
            plan.setApproveAt(LocalDateTime.now().minusDays(1));
            plan.setApprovalComment("计划清晰，同意执行");
        }
        plan.setCreatedBy(owner.getId());
        plan.setUpdatedBy(owner.getId());
        plan.setDeleted(0);
        monthPlanMapper.insert(plan);
        return plan;
    }

    private BizMonthPlanItem ensureMonthPlanItem(BizMonthPlan plan, String taskName, String taskContent, String target,
                                                  String deliverable, String standard, BigDecimal hours, LocalDate deadline) {
        BizMonthPlanItem existing = monthPlanItemMapper.selectOne(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setMonthPlanId(plan.getId());
        item.setTaskName(taskName);
        item.setTaskContent(taskContent);
        item.setTarget(target);
        item.setProgress("按计划推进");
        item.setDeliverable(deliverable);
        item.setAcceptanceStandard(standard);
        item.setEstimatedHours(hours);
        item.setDeadline(deadline);
        item.setCompletionRate("APPROVED".equals(plan.getStatus()) ? 75 : 40);
        item.setStatus(plan.getStatus());
        item.setSortNo(1);
        item.setCreatedBy(plan.getOwnerUserId());
        item.setUpdatedBy(plan.getOwnerUserId());
        item.setDeleted(0);
        monthPlanItemMapper.insert(item);
        return item;
    }

    private void ensureDayPlan(SysUser owner, BizMonthPlan plan, BizMonthPlanItem item, LocalDate date,
                               String content, String reviewStatus) {
        if (findDayPlan(owner.getId(), date) != null) {
            return;
        }
        BizDayPlan dayPlan = new BizDayPlan();
        dayPlan.setTitle(date + " 日计划");
        dayPlan.setPlanDate(date);
        dayPlan.setContent(content);
        dayPlan.setRemark("数据库演示数据");
        dayPlan.setMonthPlanId(plan.getId());
        dayPlan.setMonthPlanItemId(item.getId());
        dayPlan.setOwnerUserId(owner.getId());
        dayPlan.setDeptId(owner.getDeptId());
        dayPlan.setStatus("RISK_MARKED".equals(reviewStatus) ? "PENDING" : "PENDING");
        dayPlan.setSubmitAt(LocalDateTime.now().minusHours(6));
        dayPlan.setApprovalDueAt(LocalDateTime.now().plusHours("RISK_MARKED".equals(reviewStatus) ? -2 : 18));
        dayPlan.setReviewStatus(reviewStatus);
        dayPlan.setRiskLevel("RISK_MARKED".equals(reviewStatus) ? "HIGH" : "LOW");
        dayPlan.setAiCheckResult("NORMAL");
        dayPlan.setApprovalComment("RISK_MARKED".equals(reviewStatus) ? "客户环境存在阻断风险" : null);
        dayPlan.setCreatedBy(owner.getId());
        dayPlan.setUpdatedBy(owner.getId());
        dayPlan.setDeleted(0);
        dayPlanMapper.insert(dayPlan);
    }

    private BizDayPlan findDayPlan(Long ownerId, LocalDate date) {
        return dayPlanMapper.selectOne(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, ownerId)
                .eq(BizDayPlan::getPlanDate, date)
                .last("LIMIT 1"));
    }

    private BizResult ensureResult(SysUser owner, BizMonthPlan plan, BizMonthPlanItem item, int completionRate,
                                   String suggestionStatus, String status) {
        BizResult existing = resultMapper.selectOne(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, owner.getId())
                .eq(BizResult::getPlanId, plan.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        BizResult result = new BizResult();
        result.setTitle(item.getTaskName() + " 成果");
        result.setResultDate(LocalDate.now());
        result.setContent("已完成计划工作并提交成果证据");
        result.setCompletionRate(completionRate);
        result.setVersionNo("V1");
        result.setPlanType("MONTH");
        result.setPlanId(plan.getId());
        result.setMonthPlanItemId(item.getId());
        result.setTemporary(false);
        result.setOwnerUserId(owner.getId());
        result.setDeptId(owner.getDeptId());
        result.setStatus(status);
        result.setSubmitAt(LocalDateTime.now().minusHours(4));
        result.setEvidenceStatus("COMPLETE");
        result.setAutoLevel(completionRate >= 100 ? "DONE" : "BASIC_DONE");
        result.setIssueCodes("[]");
        result.setIssueText("");
        result.setSuggestionStatus(suggestionStatus);
        result.setCreatedBy(owner.getId());
        result.setUpdatedBy(owner.getId());
        result.setDeleted(0);
        resultMapper.insert(result);
        return result;
    }

    private void ensureEvidence(BizResult result, SysUser owner, String fileName) throws Exception {
        Long count = resultEvidenceMapper.selectCount(new LambdaQueryWrapper<BizResultEvidence>()
                .eq(BizResultEvidence::getDeleted, 0)
                .eq(BizResultEvidence::getResultId, result.getId()));
        if (count > 0) {
            return;
        }
        Path relative = Paths.get(String.valueOf(result.getId()), fileName);
        Path target = Paths.get(uploadRootPath).toAbsolutePath().normalize().resolve(relative);
        Files.createDirectories(target.getParent());
        byte[] content = minimalPdf("Planning Platform Demo Result");
        Files.write(target, content);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setResultId(result.getId());
        evidence.setFileName(fileName);
        evidence.setFileUrl(relative.toString().replace('\\', '/'));
        evidence.setFileType("pdf");
        evidence.setStatus("UPLOADED");
        evidence.setReviewPassed(false);
        evidence.setFileSize((long) content.length);
        evidence.setCreatedBy(owner.getId());
        evidence.setDeleted(0);
        resultEvidenceMapper.insert(evidence);
    }

    private void ensureAdjustment(SysUser owner, BizMonthPlan plan) {
        Long count = adjustmentMapper.selectCount(new LambdaQueryWrapper<BizPlanAdjustment>()
                .eq(BizPlanAdjustment::getDeleted, 0)
                .eq(BizPlanAdjustment::getOriginalPlanId, plan.getId()));
        if (count > 0) {
            return;
        }
        BizPlanAdjustment item = new BizPlanAdjustment();
        item.setAdjustmentNo("ADJ-DEMO-" + plan.getId());
        item.setOriginalPlanType("MONTH");
        item.setOriginalPlanId(plan.getId());
        item.setOriginalPlanNo("MP-" + plan.getPlanMonth() + "-" + plan.getId());
        item.setOriginalWorkContent(plan.getContent());
        item.setOwnerUserId(owner.getId());
        item.setDeptId(owner.getDeptId());
        item.setAdjustmentType("PAUSE");
        item.setReason("客户验收窗口调整，申请暂停两天");
        item.setImpactText("交付日期顺延，不影响证据链");
        item.setStatus("PENDING");
        item.setKeepEvidenceChain(true);
        item.setCreatedBy(owner.getId());
        item.setUpdatedBy(owner.getId());
        item.setDeleted(0);
        adjustmentMapper.insert(item);
    }

    private void ensureTodo(String sceneCode, String title, String trigger, SysUser receiver, String objectType,
                            String objectId, String routeHint, Long deptId) {
        Long count = todoMapper.selectCount(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiver.getId())
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId));
        if (count > 0) {
            return;
        }
        BizTodo todo = new BizTodo();
        todo.setSceneCode(sceneCode);
        todo.setTitle(title);
        todo.setTriggerText(trigger);
        todo.setReceiverId(receiver.getId());
        todo.setReceiverName(receiver.getRealName());
        todo.setObjectType(objectType);
        todo.setObjectId(objectId);
        todo.setDueAt(LocalDateTime.now().plusDays(1));
        todo.setRequirementText("请在截止时间前完成处理");
        todo.setImpactText("影响计划成果闭环");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint(routeHint);
        todo.setDeptId(deptId);
        todo.setCreatedBy(receiver.getId());
        todo.setUpdatedBy(receiver.getId());
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }

    private void ensureConfiguration(SysUser owner) {
        BizDeliverableTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<BizDeliverableTemplate>()
                .eq(BizDeliverableTemplate::getDeleted, 0)
                .eq(BizDeliverableTemplate::getDeptId, owner.getDeptId())
                .last("LIMIT 1"));
        if (template == null) {
            template = new BizDeliverableTemplate();
            template.setDeptId(owner.getDeptId());
            template.setTemplateName("标准成果报告模板");
            template.setEvidenceType("DOCUMENT");
            template.setRequiredFlag(true);
            template.setAppliesTo("MONTH_PLAN,RESULT");
            template.setDescription("用于月计划成果说明、评审结论和验收记录");
            template.setVersionNo("v1");
            template.setStatus("ENABLED");
            template.setReferenceCount(0);
            template.setCreatedBy(owner.getId());
            template.setUpdatedBy(owner.getId());
            template.setDeleted(0);
            templateMapper.insert(template);
        }
        Long standards = standardMapper.selectCount(new LambdaQueryWrapper<BizAcceptanceStandard>()
                .eq(BizAcceptanceStandard::getDeleted, 0)
                .eq(BizAcceptanceStandard::getTemplateId, template.getId()));
        if (standards == 0) {
            BizAcceptanceStandard standard = new BizAcceptanceStandard();
            standard.setTemplateId(template.getId());
            standard.setStandardText("成果内容完整，评审通过，关键结论可追溯");
            standard.setRequireReviewPassed(true);
            standard.setEvidenceRequirement("至少上传一份 PDF、Word 或 Zip 成果证据");
            standard.setVersionNo("v1");
            standard.setStatus("ENABLED");
            standard.setCreatedBy(owner.getId());
            standard.setUpdatedBy(owner.getId());
            standard.setDeleted(0);
            standardMapper.insert(standard);
        }
        Long rules = scoreRuleMapper.selectCount(new LambdaQueryWrapper<BizScoreRule>()
                .eq(BizScoreRule::getDeleted, 0)
                .eq(BizScoreRule::getDeptId, owner.getDeptId()));
        if (rules == 0) {
            BizScoreRule rule = new BizScoreRule();
            rule.setDeptId(owner.getDeptId());
            rule.setRuleName("产品中心成果参考分规则");
            rule.setStatus("ENABLED");
            rule.setEffectiveStart(LocalDate.now().withDayOfMonth(1));
            rule.setRuleJson(jsonCodec.write(Map.of("factors", java.util.List.of(
                    Map.of("code", "completion_ratio", "name", "完成比例", "weight", 70, "enabled", true),
                    Map.of("code", "overdue_count", "name", "逾期提交", "weight", 10,
                            "penaltyPerTime", 2, "enabled", true),
                    Map.of("code", "reject_count", "name", "驳回次数", "weight", 10,
                            "penaltyPerTime", 3, "enabled", true),
                    Map.of("code", "review_passed", "name", "评审通过", "weight", 10, "enabled", true)
            ))));
            rule.setCreatedBy(owner.getId());
            rule.setUpdatedBy(owner.getId());
            rule.setDeleted(0);
            scoreRuleMapper.insert(rule);
        }
    }

    private byte[] minimalPdf(String text) {
        String escaped = text.replace("(", "\\(").replace(")", "\\)");
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n"
                + "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n"
                + "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources<< /Font<< /F1 4 0 R >> >> /Contents 5 0 R >>endobj\n"
                + "4 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n"
                + "5 0 obj<< /Length " + (escaped.length() + 36) + " >>stream\nBT /F1 16 Tf 72 720 Td (" + escaped + ") Tj ET\nendstream endobj\n"
                + "xref\n0 6\n0000000000 65535 f \n"
                + "trailer<< /Root 1 0 R /Size 6 >>\nstartxref\n0\n%%EOF";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }
}
