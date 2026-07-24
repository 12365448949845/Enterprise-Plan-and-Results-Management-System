package com.planning.platform.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.dto.AuditDecisionReqDTO;
import com.planning.platform.planning.dto.DayPlanSaveReqDTO;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DayPlanService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final BizDayPlanMapper dayPlanMapper;
    private final PlanningAccessService accessService;
    private final AuditLogService auditLogService;

    public List<BizDayPlan> list(AuthUser user, String status, LocalDate startDate, LocalDate endDate, String keyword, Boolean mine) {
        LambdaQueryWrapper<BizDayPlan> query = new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(StringUtils.hasText(status), BizDayPlan::getStatus, status)
                .ge(startDate != null, BizDayPlan::getPlanDate, startDate)
                .le(endDate != null, BizDayPlan::getPlanDate, endDate)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(BizDayPlan::getTitle, keyword)
                        .or()
                        .like(BizDayPlan::getContent, keyword))
                .orderByDesc(BizDayPlan::getPlanDate)
                .orderByDesc(BizDayPlan::getId);
        if (Boolean.TRUE.equals(mine)) {
            query.eq(BizDayPlan::getOwnerUserId, user.userId());
        } else {
            query.in(BizDayPlan::getOwnerUserId, accessService.accessibleOwnerIds(user));
        }
        return dayPlanMapper.selectList(query);
    }

    @Transactional
    public BizDayPlan create(AuthUser user, DayPlanSaveReqDTO request) {
        BizDayPlan plan = new BizDayPlan();
        fill(plan, request);
        plan.setOwnerUserId(user.userId());
        plan.setDeptId(user.deptId());
        plan.setStatus(DRAFT);
        plan.setCreatedBy(user.userId());
        plan.setUpdatedBy(user.userId());
        plan.setDeleted(0);
        dayPlanMapper.insert(plan);
        auditLogService.success(user, "DAY_PLAN_CREATE", "DAY_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizDayPlan update(AuthUser user, Long id, DayPlanSaveReqDTO request) {
        BizDayPlan plan = requirePlan(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可编辑");
        }
        fill(plan, request);
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        auditLogService.success(user, "DAY_PLAN_UPDATE", "DAY_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizDayPlan submit(AuthUser user, Long id) {
        BizDayPlan plan = requirePlan(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可提交");
        }
        plan.setStatus(PENDING);
        plan.setSubmitAt(LocalDateTime.now());
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        auditLogService.success(user, "DAY_PLAN_SUBMIT", "DAY_PLAN", plan.getId(), "{}");
        return plan;
    }

    @Transactional
    public BizDayPlan approve(AuthUser user, Long id, AuditDecisionReqDTO request) {
        BizDayPlan plan = requirePlan(id);
        accessService.requireManage(user, plan.getOwnerUserId());
        if (!PENDING.equals(plan.getStatus())) {
            throw new BizException("只有待审批状态可审批");
        }
        plan.setStatus(Boolean.TRUE.equals(request.getApproved()) ? APPROVED : REJECTED);
        plan.setApproverId(user.userId());
        plan.setApproveAt(LocalDateTime.now());
        plan.setApprovalComment(request.getComment());
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        auditLogService.success(user, "DAY_PLAN_APPROVE", "DAY_PLAN", plan.getId(),
                "{\"approved\":" + Boolean.TRUE.equals(request.getApproved()) + "}");
        return plan;
    }

    private BizDayPlan requirePlan(Long id) {
        BizDayPlan plan = dayPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划不存在");
        }
        return plan;
    }

    private void fill(BizDayPlan plan, DayPlanSaveReqDTO request) {
        plan.setTitle(request.getTitle());
        plan.setPlanDate(request.getPlanDate());
        plan.setContent(request.getContent());
        plan.setMonthPlanId(request.getMonthPlanId());
    }
}
