package com.planning.platform.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.dto.AuditDecisionReqDTO;
import com.planning.platform.planning.dto.ResultSaveReqDTO;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResultService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";

    private final BizResultMapper resultMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final PlanningAccessService accessService;
    private final AuditLogService auditLogService;

    public List<BizResult> list(AuthUser user, String status, LocalDate startDate, LocalDate endDate, String keyword, Boolean mine) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(StringUtils.hasText(status), BizResult::getStatus, status)
                .ge(startDate != null, BizResult::getResultDate, startDate)
                .le(endDate != null, BizResult::getResultDate, endDate)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(BizResult::getTitle, keyword)
                        .or()
                        .like(BizResult::getContent, keyword))
                .orderByDesc(BizResult::getResultDate)
                .orderByDesc(BizResult::getId);
        if (Boolean.TRUE.equals(mine)) {
            query.eq(BizResult::getOwnerUserId, user.userId());
        } else {
            query.in(BizResult::getOwnerUserId, accessService.accessibleOwnerIds(user));
        }
        return resultMapper.selectList(query);
    }

    @Transactional
    public BizResult create(AuthUser user, ResultSaveReqDTO request) {
        validatePlanBinding(user.userId(), request);
        BizResult result = new BizResult();
        fill(result, request);
        result.setOwnerUserId(user.userId());
        result.setDeptId(user.deptId());
        result.setStatus(DRAFT);
        result.setCreatedBy(user.userId());
        result.setUpdatedBy(user.userId());
        result.setDeleted(0);
        resultMapper.insert(result);
        auditLogService.success(user, "RESULT_CREATE", "RESULT", result.getId(), "{}");
        return result;
    }

    @Transactional
    public BizResult update(AuthUser user, Long id, ResultSaveReqDTO request) {
        BizResult result = requireResult(id);
        accessService.requireOwner(user, result.getOwnerUserId());
        validatePlanBinding(result.getOwnerUserId(), request);
        if (!DRAFT.equals(result.getStatus()) && !REJECTED.equals(result.getStatus())) {
            throw new BizException("只有草稿或驳回状态可编辑");
        }
        fill(result, request);
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        auditLogService.success(user, "RESULT_UPDATE", "RESULT", result.getId(), "{}");
        return result;
    }

    @Transactional
    public BizResult submit(AuthUser user, Long id) {
        BizResult result = requireResult(id);
        accessService.requireOwner(user, result.getOwnerUserId());
        if (!DRAFT.equals(result.getStatus()) && !REJECTED.equals(result.getStatus())) {
            throw new BizException("只有草稿或驳回状态可提交");
        }
        result.setStatus(PENDING);
        result.setSubmitAt(LocalDateTime.now());
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        auditLogService.success(user, "RESULT_SUBMIT", "RESULT", result.getId(), "{}");
        return result;
    }

    @Transactional
    public BizResult confirm(AuthUser user, Long id, AuditDecisionReqDTO request) {
        BizResult result = requireResult(id);
        accessService.requireManage(user, result.getOwnerUserId());
        if (!PENDING.equals(result.getStatus())) {
            throw new BizException("只有待确认状态可确认");
        }
        result.setStatus(Boolean.TRUE.equals(request.getApproved()) ? CONFIRMED : REJECTED);
        result.setConfirmerId(user.userId());
        result.setConfirmAt(LocalDateTime.now());
        result.setConfirmComment(request.getComment());
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        auditLogService.success(user, "RESULT_CONFIRM", "RESULT", result.getId(),
                "{\"approved\":" + Boolean.TRUE.equals(request.getApproved()) + "}");
        return result;
    }

    private BizResult requireResult(Long id) {
        BizResult result = resultMapper.selectById(id);
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果不存在");
        }
        return result;
    }

    private void validatePlanBinding(Long ownerUserId, ResultSaveReqDTO request) {
        boolean temporary = Boolean.TRUE.equals(request.getTemporary());
        if (temporary) {
            if (!StringUtils.hasText(request.getTemporaryReason())) {
                throw new BizException("临时成果必须填写原因");
            }
            return;
        }
        if (!StringUtils.hasText(request.getPlanType()) || request.getPlanId() == null) {
            throw new BizException("成果原则上必须关联计划；如为临时成果请勾选临时成果并填写原因");
        }
        String planType = request.getPlanType().toUpperCase(Locale.ROOT);
        if ("MONTH".equals(planType)) {
            BizMonthPlan plan = monthPlanMapper.selectById(request.getPlanId());
            if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
                throw new BizException(404, "关联月计划不存在");
            }
            if (!ownerUserId.equals(plan.getOwnerUserId())) {
                throw new BizException(403, "只能关联成果所属员工本人的月计划");
            }
            if (!APPROVED.equals(plan.getStatus())) {
                throw new BizException(422, "月计划审批通过后才能关联成果");
            }
            return;
        }
        if ("DAY".equals(planType)) {
            BizDayPlan plan = dayPlanMapper.selectById(request.getPlanId());
            if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
                throw new BizException(404, "关联日计划不存在");
            }
            if (!ownerUserId.equals(plan.getOwnerUserId())) {
                throw new BizException(403, "只能关联成果所属员工本人的日计划");
            }
            if (!APPROVED.equals(plan.getStatus())) {
                throw new BizException(422, "日计划点评通过后才能关联成果");
            }
            return;
        }
        throw new BizException(422, "计划类型仅支持 DAY 或 MONTH");
    }

    private void fill(BizResult result, ResultSaveReqDTO request) {
        boolean temporary = Boolean.TRUE.equals(request.getTemporary());
        result.setTitle(request.getTitle());
        result.setResultDate(request.getResultDate());
        result.setContent(request.getContent());
        result.setTemporary(temporary);
        result.setTemporaryReason(temporary ? request.getTemporaryReason() : null);
        result.setPlanType(temporary ? "TEMP" : request.getPlanType().toUpperCase(Locale.ROOT));
        result.setPlanId(temporary ? null : request.getPlanId());
    }
}
