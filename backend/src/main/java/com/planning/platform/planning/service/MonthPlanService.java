package com.planning.platform.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.dto.AuditDecisionReqDTO;
import com.planning.platform.planning.dto.MonthPlanSaveReqDTO;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonthPlanService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final BizMonthPlanMapper monthPlanMapper;
    private final PlanningAccessService accessService;
    private final AuditLogService auditLogService;

    public List<BizMonthPlan> list(AuthUser user, String status, String planMonth, String keyword, Boolean mine) {
        LambdaQueryWrapper<BizMonthPlan> query = new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(StringUtils.hasText(status), BizMonthPlan::getStatus, status)
                .eq(StringUtils.hasText(planMonth), BizMonthPlan::getPlanMonth, planMonth)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(BizMonthPlan::getTitle, keyword)
                        .or()
                        .like(BizMonthPlan::getContent, keyword))
                .orderByDesc(BizMonthPlan::getPlanMonth)
                .orderByDesc(BizMonthPlan::getId);
        if (Boolean.TRUE.equals(mine)) {
            query.eq(BizMonthPlan::getOwnerUserId, user.userId());
        } else {
            query.in(BizMonthPlan::getOwnerUserId, accessService.accessibleOwnerIds(user));
        }
        return monthPlanMapper.selectList(query);
    }

    @Transactional
    public BizMonthPlan create(AuthUser user, MonthPlanSaveReqDTO request) {
        BizMonthPlan plan = new BizMonthPlan();
        fill(plan, request);
        plan.setOwnerUserId(user.userId());
        plan.setDeptId(user.deptId());
        plan.setStatus(DRAFT);
        plan.setCreatedBy(user.userId());
        plan.setUpdatedBy(user.userId());
        plan.setDeleted(0);
        monthPlanMapper.insert(plan);
        auditLogService.success(user, "MONTH_PLAN_CREATE", "MONTH_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizMonthPlan update(AuthUser user, Long id, MonthPlanSaveReqDTO request) {
        BizMonthPlan plan = requirePlan(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可编辑");
        }
        fill(plan, request);
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        auditLogService.success(user, "MONTH_PLAN_UPDATE", "MONTH_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizMonthPlan submit(AuthUser user, Long id) {
        BizMonthPlan plan = requirePlan(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可提交");
        }
        requireWritableMonth(plan.getPlanMonth());
        plan.setStatus(PENDING);
        plan.setSubmitAt(LocalDateTime.now());
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        auditLogService.success(user, "MONTH_PLAN_SUBMIT", "MONTH_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizMonthPlan approve(AuthUser user, Long id, AuditDecisionReqDTO request) {
        BizMonthPlan plan = requirePlan(id);
        accessService.requireManage(user, plan.getOwnerUserId());
        if (!PENDING.equals(plan.getStatus())) {
            throw new BizException("只有待审批状态可审批");
        }
        plan.setStatus(Boolean.TRUE.equals(request.getApproved()) ? APPROVED : REJECTED);
        plan.setApproverId(user.userId());
        plan.setApproveAt(LocalDateTime.now());
        plan.setApprovalComment(request.getComment());
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        auditLogService.success(user, "MONTH_PLAN_APPROVE", "MONTH_PLAN", plan.getId(),
                "{\"approved\":" + Boolean.TRUE.equals(request.getApproved()) + "}");
        return plan;
    }

    private BizMonthPlan requirePlan(Long id) {
        BizMonthPlan plan = monthPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划不存在");
        }
        return plan;
    }

    private void fill(BizMonthPlan plan, MonthPlanSaveReqDTO request) {
        plan.setTitle(request.getTitle());
        if (!StringUtils.hasText(request.getPlanMonth())) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
        try {
            YearMonth planMonth = YearMonth.parse(request.getPlanMonth().trim());
            requireWritableMonth(planMonth.toString());
            plan.setPlanMonth(planMonth.toString());
        } catch (DateTimeException ex) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
        plan.setContent(request.getContent());
    }

    private void requireWritableMonth(String planMonth) {
        YearMonth month;
        try {
            month = YearMonth.parse(planMonth);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
        if (month.isBefore(YearMonth.now(BUSINESS_ZONE))) {
            throw new BizException(422, "月计划只能填写当前月份及以后的月份");
        }
    }
}
