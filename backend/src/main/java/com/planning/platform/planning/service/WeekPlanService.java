package com.planning.platform.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.ai.service.AiReviewService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizWeekPlan;
import com.planning.platform.planning.domain.BizWeekPlanItem;
import com.planning.platform.planning.dto.WeekPlanDecisionReqDTO;
import com.planning.platform.planning.dto.WeekPlanSaveReqDTO;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizWeekPlanItemMapper;
import com.planning.platform.planning.mapper.BizWeekPlanMapper;
import com.planning.platform.planning.vo.WeekPlanVO.ActionVO;
import com.planning.platform.planning.vo.WeekPlanVO.DetailVO;
import com.planning.platform.planning.vo.WeekPlanVO.ItemVO;
import com.planning.platform.planning.vo.WeekPlanVO.ParentOptionVO;
import com.planning.platform.planning.vo.WeekPlanVO.SummaryVO;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeekPlanService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ALL_STATUSES = Set.of(DRAFT, PENDING, APPROVED, REJECTED);
    private static final Set<String> DECIDED_STATUSES = Set.of(APPROVED, REJECTED);

    private final BizWeekPlanMapper weekPlanMapper;
    private final BizWeekPlanItemMapper weekPlanItemMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizTodoMapper todoMapper;
    private final PerformanceRoleGuard roleGuard;
    private final PerformanceDataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final UserMessageService messageService;
    private final AiReviewService aiReviewService;

    public List<SummaryVO> employeePlans(AuthUser user, String status, LocalDate weekStart) {
        roleGuard.requireEmployeeModule(user);
        String normalizedStatus = normalizeStatus(status, ALL_STATUSES);
        List<BizWeekPlan> plans = weekPlanMapper.selectList(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .eq(BizWeekPlan::getOwnerUserId, user.userId())
                .eq(normalizedStatus != null, BizWeekPlan::getStatus, normalizedStatus)
                .eq(weekStart != null, BizWeekPlan::getWeekStart, weekStart)
                .orderByDesc(BizWeekPlan::getWeekStart)
                .orderByDesc(BizWeekPlan::getId));
        return summaries(plans);
    }

    public DetailVO employeeDetail(AuthUser user, Long id) {
        roleGuard.requireEmployeeModule(user);
        BizWeekPlan plan = requirePlan(id);
        requireOwner(user, plan);
        return detail(plan, false);
    }

    public List<ParentOptionVO> parentOptions(AuthUser user) {
        roleGuard.requireEmployeeModule(user);
        return parentOptionsForOwner(user.userId());
    }

    @Transactional
    public DetailVO createDraft(AuthUser user, WeekPlanSaveReqDTO request) {
        roleGuard.requireEmployeeModule(user);
        LocalDate weekStart = requireWeekStart(request.getWeekStart());
        ensureNoDuplicate(user.userId(), weekStart, null);
        validateItems(user, weekStart, request.getItems());

        LocalDateTime now = LocalDateTime.now();
        BizWeekPlan plan = new BizWeekPlan();
        plan.setTitle(weekTitle(weekStart));
        plan.setWeekStart(weekStart);
        plan.setWeekEnd(weekStart.plusDays(6));
        plan.setOwnerUserId(user.userId());
        plan.setDeptId(user.deptId());
        plan.setStatus(DRAFT);
        plan.setVersionNo(1);
        plan.setCreatedBy(user.userId());
        plan.setUpdatedBy(user.userId());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        plan.setDeleted(0);
        try {
            weekPlanMapper.insert(plan);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "该自然周已存在周计划，请打开原计划继续编辑");
        }
        replaceItems(plan, request.getItems(), user.userId(), now);
        auditLogService.success(user, "WEEK_PLAN_SAVE", "WEEK_PLAN", plan.getId(),
                "{\"status\":\"DRAFT\",\"weekStart\":\"" + weekStart + "\"}");
        return detail(plan, false);
    }

    @Transactional
    public DetailVO updateDraft(AuthUser user, Long id, WeekPlanSaveReqDTO request) {
        roleGuard.requireEmployeeModule(user);
        BizWeekPlan plan = requirePlanForUpdate(id);
        requireOwner(user, plan);
        requireVersion(plan, request.getVersionNo());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException(409, "只有草稿或已驳回的周计划可以编辑");
        }
        LocalDate weekStart = requireWeekStart(request.getWeekStart());
        ensureNoDuplicate(user.userId(), weekStart, id);
        validateItems(user, weekStart, request.getItems());

        LocalDateTime now = LocalDateTime.now();
        plan.setTitle(weekTitle(weekStart));
        plan.setWeekStart(weekStart);
        plan.setWeekEnd(weekStart.plusDays(6));
        plan.setStatus(DRAFT);
        plan.setVersionNo(plan.getVersionNo() + 1);
        plan.setSubmitAt(null);
        plan.setApproverId(null);
        plan.setApproveAt(null);
        plan.setApprovalComment(null);
        plan.setUpdatedBy(user.userId());
        plan.setUpdatedAt(now);
        weekPlanMapper.updateById(plan);
        replaceItems(plan, request.getItems(), user.userId(), now);
        auditLogService.success(user, "WEEK_PLAN_SAVE", "WEEK_PLAN", plan.getId(),
                "{\"status\":\"DRAFT\",\"versionNo\":" + plan.getVersionNo() + "}");
        return detail(plan, false);
    }

    @Transactional
    public ActionVO submit(AuthUser user, Long id, Integer versionNo) {
        roleGuard.requireEmployeeModule(user);
        BizWeekPlan plan = requirePlanForUpdate(id);
        requireOwner(user, plan);
        requireVersion(plan, versionNo);
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException(409, "只有草稿或已驳回的周计划可以提交");
        }
        List<BizWeekPlanItem> items = items(id);
        if (items.isEmpty()) {
            throw new BizException(422, "请至少填写一条周计划");
        }
        validateStoredItems(user, plan, items);
        aiReviewService.ensurePlanReview(user, AiReviewService.WEEK_PLAN, plan.getId());
        Long leaderId = dataScopeService.directLeaderId(user.userId());
        if (leaderId == null || leaderId.equals(user.userId())) {
            throw new BizException(422, "当前员工未配置直属领导，无法提交周计划审批");
        }

        LocalDateTime now = LocalDateTime.now();
        plan.setStatus(PENDING);
        plan.setVersionNo(plan.getVersionNo() + 1);
        plan.setSubmitAt(now);
        plan.setApproverId(leaderId);
        plan.setApproveAt(null);
        plan.setApprovalComment(null);
        plan.setUpdatedBy(user.userId());
        plan.setUpdatedAt(now);
        weekPlanMapper.updateById(plan);
        createApprovalTodo(plan, leaderId, user, now);
        auditLogService.success(user, "WEEK_PLAN_SUBMIT", "WEEK_PLAN", plan.getId(),
                "{\"status\":\"PENDING\",\"approverId\":" + leaderId + "}");
        return new ActionVO(plan.getId(), plan.getStatus(), plan.getVersionNo(), "周计划已提交直属领导审批");
    }

    @Transactional
    public ActionVO withdraw(AuthUser user, Long id, Integer versionNo) {
        roleGuard.requireEmployeeModule(user);
        BizWeekPlan plan = requirePlanForUpdate(id);
        requireOwner(user, plan);
        requireVersion(plan, versionNo);
        if (!PENDING.equals(plan.getStatus())) {
            throw new BizException(409, "只有待审批的周计划可以撤回");
        }
        plan.setStatus(DRAFT);
        plan.setVersionNo(plan.getVersionNo() + 1);
        plan.setSubmitAt(null);
        plan.setApproverId(null);
        plan.setUpdatedBy(user.userId());
        plan.setUpdatedAt(LocalDateTime.now());
        weekPlanMapper.updateById(plan);
        notifyReadApproverOfWithdrawal(user, plan);
        completeTodos(plan.getId());
        auditLogService.success(user, "WEEK_PLAN_WITHDRAW", "WEEK_PLAN", plan.getId(),
                "{\"status\":\"DRAFT\"}");
        return new ActionVO(plan.getId(), plan.getStatus(), plan.getVersionNo(), "周计划已撤回为草稿");
    }

    @Transactional
    public ActionVO deleteDraft(AuthUser user, Long id, Integer versionNo) {
        roleGuard.requireEmployeeModule(user);
        BizWeekPlan plan = requirePlanForUpdate(id);
        requireOwner(user, plan);
        requireVersion(plan, versionNo);
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException(409, "只有草稿或已驳回的周计划可以删除");
        }
        plan.setVersionNo(plan.getVersionNo() + 1);
        weekPlanItemMapper.delete(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getWeekPlanId, plan.getId()));
        weekPlanMapper.deleteById(plan.getId());
        completeTodos(plan.getId());
        auditLogService.success(user, "WEEK_PLAN_DELETE", "WEEK_PLAN", plan.getId(),
                "{\"previousStatus\":\"" + plan.getStatus() + "\"}");
        return new ActionVO(plan.getId(), plan.getStatus(), plan.getVersionNo(), "周计划草稿已删除，审计记录继续保留");
    }

    public List<SummaryVO> leaderPlans(AuthUser user, Long deptId, String status, LocalDate weekStart) {
        roleGuard.requireLeaderModule(user);
        String normalizedStatus = normalizeStatus(status, Set.of(PENDING, APPROVED, REJECTED));
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, deptId);
        if (user.roles() == null || !user.roles().contains("SUPER_ADMIN")) {
            ownerIds.removeIf(ownerId -> !user.userId().equals(dataScopeService.directLeaderId(ownerId)));
        }
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        List<BizWeekPlan> plans = weekPlanMapper.selectList(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .in(BizWeekPlan::getOwnerUserId, ownerIds)
                .eq(normalizedStatus != null, BizWeekPlan::getStatus, normalizedStatus)
                .eq(weekStart != null, BizWeekPlan::getWeekStart, weekStart)
                .orderByAsc(BizWeekPlan::getStatus)
                .orderByDesc(BizWeekPlan::getWeekStart));
        return summaries(plans);
    }

    public DetailVO leaderDetail(AuthUser user, Long id) {
        roleGuard.requireLeaderModule(user);
        BizWeekPlan plan = requirePlan(id);
        requireDirectLeader(user, plan);
        return detail(plan, true);
    }

    @Transactional
    public ActionVO approve(AuthUser user, Long id, WeekPlanDecisionReqDTO request) {
        return decide(user, id, request, APPROVED);
    }

    @Transactional
    public ActionVO reject(AuthUser user, Long id, WeekPlanDecisionReqDTO request) {
        if (!StringUtils.hasText(request.getComment())) {
            throw new BizException(422, "驳回原因不能为空");
        }
        return decide(user, id, request, REJECTED);
    }

    public List<SummaryVO> departmentPlans(AuthUser user, Long deptId, String status, LocalDate weekStart) {
        roleGuard.requireDepartmentModule(user);
        String normalizedStatus = normalizeStatus(status, DECIDED_STATUSES);
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, deptId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<BizWeekPlan> query = new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .in(BizWeekPlan::getOwnerUserId, ownerIds)
                .in(normalizedStatus == null, BizWeekPlan::getStatus, DECIDED_STATUSES)
                .eq(normalizedStatus != null, BizWeekPlan::getStatus, normalizedStatus)
                .eq(weekStart != null, BizWeekPlan::getWeekStart, weekStart)
                .orderByDesc(BizWeekPlan::getWeekStart)
                .orderByDesc(BizWeekPlan::getApproveAt);
        return summaries(weekPlanMapper.selectList(query));
    }

    public DetailVO departmentDetail(AuthUser user, Long id) {
        roleGuard.requireDepartmentModule(user);
        BizWeekPlan plan = requirePlan(id);
        dataScopeService.requireDepartmentOwner(user, plan.getOwnerUserId());
        if (!DECIDED_STATUSES.contains(plan.getStatus())) {
            throw new BizException(404, "周计划审批结果不存在");
        }
        return detail(plan, false);
    }

    private ActionVO decide(AuthUser user, Long id, WeekPlanDecisionReqDTO request, String targetStatus) {
        roleGuard.requireLeaderModule(user);
        BizWeekPlan plan = requirePlanForUpdate(id);
        requireDirectLeader(user, plan);
        requireVersion(plan, request.getVersionNo());
        if (!PENDING.equals(plan.getStatus())) {
            throw new BizException(409, "只有待审批的周计划可以处理");
        }
        LocalDateTime now = LocalDateTime.now();
        plan.setStatus(targetStatus);
        plan.setVersionNo(plan.getVersionNo() + 1);
        plan.setApproverId(user.userId());
        plan.setApproveAt(now);
        plan.setApprovalComment(trim(request.getComment()));
        plan.setUpdatedBy(user.userId());
        plan.setUpdatedAt(now);
        weekPlanMapper.updateById(plan);
        completeTodos(plan.getId());
        createResultNotification(plan, user, now);
        auditLogService.success(user, APPROVED.equals(targetStatus) ? "WEEK_PLAN_APPROVE" : "WEEK_PLAN_REJECT",
                "WEEK_PLAN", plan.getId(), "{\"status\":\"" + targetStatus + "\"}");
        return new ActionVO(plan.getId(), plan.getStatus(), plan.getVersionNo(),
                APPROVED.equals(targetStatus) ? "周计划已审批通过" : "周计划已驳回");
    }

    private void validateItems(AuthUser user, LocalDate weekStart, List<WeekPlanSaveReqDTO.Item> requestItems) {
        Set<Long> ids = new LinkedHashSet<>();
        for (WeekPlanSaveReqDTO.Item item : requestItems) {
            if (!ids.add(item.getMonthPlanItemId())) {
                throw new BizException(422, "同一月计划条目不能在一张周计划中重复关联");
            }
            if (item.getPlannedFinishDate() != null
                    && (item.getPlannedFinishDate().isBefore(weekStart) || item.getPlannedFinishDate().isAfter(weekStart.plusDays(6)))) {
                throw new BizException(422, "计划完成日期必须在当前自然周内");
            }
            if (item.getPlannedFinishDate() != null
                    && item.getPlannedFinishDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
                throw new BizException(422, "计划完成日期不能早于今天");
            }
            requireValidParent(user, weekStart, item.getMonthPlanItemId());
        }
    }

    private void validateStoredItems(AuthUser user, BizWeekPlan plan, List<BizWeekPlanItem> storedItems) {
        List<WeekPlanSaveReqDTO.Item> requestItems = storedItems.stream().map(stored -> {
            WeekPlanSaveReqDTO.Item item = new WeekPlanSaveReqDTO.Item();
            item.setMonthPlanItemId(stored.getMonthPlanItemId());
            item.setContent(stored.getContent());
            item.setDeliverable(stored.getDeliverable());
            item.setPlannedFinishDate(stored.getPlannedFinishDate());
            return item;
        }).toList();
        validateItems(user, plan.getWeekStart(), requestItems);
    }

    private ParentOptionVO requireValidParent(AuthUser user, LocalDate weekStart, Long monthPlanItemId) {
        BizMonthPlanItem item = monthPlanItemMapper.selectById(monthPlanItemId);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(422, "关联的月计划条目不存在");
        }
        BizMonthPlan monthPlan = monthPlanMapper.selectById(item.getMonthPlanId());
        if (monthPlan == null || Integer.valueOf(1).equals(monthPlan.getDeleted())) {
            throw new BizException(422, "关联的月计划不存在");
        }
        if (!user.userId().equals(monthPlan.getOwnerUserId()) || !Objects.equals(user.deptId(), monthPlan.getDeptId())) {
            throw new BizException(403, "不能关联其他员工或其他部门的月计划条目");
        }
        if (!APPROVED.equals(monthPlan.getStatus())) {
            throw new BizException(422, "只有已审批通过的月计划条目可以拆解为周计划");
        }
        if ("EXTRA".equals(item.getTaskType()) && !APPROVED.equals(item.getStatus())) {
            throw new BizException(422, "额外月计划条目审批通过后才能拆解为周计划");
        }
        YearMonth parentMonth;
        try {
            parentMonth = YearMonth.parse(monthPlan.getPlanMonth());
        } catch (DateTimeParseException ex) {
            throw new BizException(422, "父级月计划月份格式不正确");
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        if (weekEnd.isBefore(parentMonth.atDay(1)) || weekStart.isAfter(parentMonth.atEndOfMonth())) {
            throw new BizException(422, "当前自然周与父级月计划月份没有交集");
        }
        return parentOption(monthPlan, item);
    }

    private List<ParentOptionVO> parentOptionsForOwner(Long ownerUserId) {
        List<BizMonthPlan> monthPlans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, ownerUserId)
                .eq(BizMonthPlan::getStatus, APPROVED)
                .orderByDesc(BizMonthPlan::getPlanMonth));
        if (monthPlans.isEmpty()) {
            return List.of();
        }
        Map<Long, BizMonthPlan> monthMap = monthPlans.stream()
                .collect(Collectors.toMap(BizMonthPlan::getId, Function.identity()));
        List<BizMonthPlanItem> eligibleItems = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                        .eq(BizMonthPlanItem::getDeleted, 0)
                        .in(BizMonthPlanItem::getMonthPlanId, monthMap.keySet())
                        .orderByAsc(BizMonthPlanItem::getSortNo)
                        .orderByAsc(BizMonthPlanItem::getId)).stream()
                .filter(item -> !"EXTRA".equals(item.getTaskType()) || APPROVED.equals(item.getStatus()))
                .toList();
        if (eligibleItems.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> usedCounts = weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                        .eq(BizWeekPlanItem::getDeleted, 0)
                        .in(BizWeekPlanItem::getMonthPlanItemId, eligibleItems.stream().map(BizMonthPlanItem::getId).toList())).stream()
                .collect(Collectors.groupingBy(BizWeekPlanItem::getMonthPlanItemId, Collectors.counting()));
        return eligibleItems.stream()
                .map(item -> parentOption(monthMap.get(item.getMonthPlanId()), item,
                        usedCounts.getOrDefault(item.getId(), 0L).intValue()))
                .toList();
    }

    private void replaceItems(BizWeekPlan plan, List<WeekPlanSaveReqDTO.Item> requestItems, Long operatorId, LocalDateTime now) {
        weekPlanItemMapper.delete(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getWeekPlanId, plan.getId()));
        int sortNo = 1;
        for (WeekPlanSaveReqDTO.Item requestItem : requestItems) {
            BizWeekPlanItem item = new BizWeekPlanItem();
            item.setWeekPlanId(plan.getId());
            item.setMonthPlanItemId(requestItem.getMonthPlanItemId());
            item.setContent(requestItem.getContent().trim());
            item.setDeliverable(trim(requestItem.getDeliverable()));
            item.setAcceptanceStandard(null);
            item.setPlannedFinishDate(requestItem.getPlannedFinishDate());
            item.setSortNo(sortNo++);
            item.setCreatedBy(operatorId);
            item.setUpdatedBy(operatorId);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setDeleted(0);
            weekPlanItemMapper.insert(item);
        }
    }

    private DetailVO detail(BizWeekPlan plan, boolean includeSiblings) {
        List<BizWeekPlanItem> planItems = items(plan.getId());
        Map<Long, ParentOptionVO> parentMap = loadParents(planItems);
        List<ItemVO> itemViews = planItems.stream().map(item -> new ItemVO(
                item.getId(), item.getMonthPlanItemId(), item.getContent(), item.getDeliverable(),
                item.getPlannedFinishDate(), item.getSortNo(), parentMap.get(item.getMonthPlanItemId())
        )).toList();
        List<SummaryVO> siblings = includeSiblings ? siblingPlans(plan, planItems) : List.of();
        return new DetailVO(summary(plan, planItems.size()), itemViews, siblings, 0L);
    }

    private Map<Long, ParentOptionVO> loadParents(List<BizWeekPlanItem> planItems) {
        if (planItems.isEmpty()) {
            return Map.of();
        }
        List<BizMonthPlanItem> monthItems = monthPlanItemMapper.selectBatchIds(planItems.stream()
                .map(BizWeekPlanItem::getMonthPlanItemId).collect(Collectors.toSet()));
        Map<Long, BizMonthPlan> plans = monthPlanMapper.selectBatchIds(monthItems.stream()
                        .map(BizMonthPlanItem::getMonthPlanId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(BizMonthPlan::getId, Function.identity()));
        Map<Long, ParentOptionVO> result = new LinkedHashMap<>();
        for (BizMonthPlanItem item : monthItems) {
            BizMonthPlan monthPlan = plans.get(item.getMonthPlanId());
            if (monthPlan != null) {
                result.put(item.getId(), parentOption(monthPlan, item));
            }
        }
        return result;
    }

    private List<SummaryVO> siblingPlans(BizWeekPlan current, List<BizWeekPlanItem> currentItems) {
        if (currentItems.isEmpty()) {
            return List.of();
        }
        Set<Long> parentIds = currentItems.stream().map(BizWeekPlanItem::getMonthPlanItemId).collect(Collectors.toSet());
        Set<Long> weekPlanIds = weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                        .eq(BizWeekPlanItem::getDeleted, 0)
                        .in(BizWeekPlanItem::getMonthPlanItemId, parentIds)).stream()
                .map(BizWeekPlanItem::getWeekPlanId)
                .filter(id -> !id.equals(current.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (weekPlanIds.isEmpty()) {
            return List.of();
        }
        return summaries(weekPlanMapper.selectList(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .in(BizWeekPlan::getId, weekPlanIds)
                .orderByDesc(BizWeekPlan::getWeekStart)));
    }

    private List<SummaryVO> summaries(List<BizWeekPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                        .eq(BizWeekPlanItem::getDeleted, 0)
                        .in(BizWeekPlanItem::getWeekPlanId, plans.stream().map(BizWeekPlan::getId).toList())).stream()
                .collect(Collectors.groupingBy(BizWeekPlanItem::getWeekPlanId, Collectors.counting()));
        return plans.stream().map(plan -> summary(plan, counts.getOrDefault(plan.getId(), 0L).intValue())).toList();
    }

    private SummaryVO summary(BizWeekPlan plan, int itemCount) {
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        return new SummaryVO(plan.getId(), plan.getTitle(), plan.getWeekStart(), plan.getWeekEnd(), plan.getStatus(),
                plan.getVersionNo(), plan.getOwnerUserId(), owner.getRealName(), plan.getDeptId(),
                dataScopeService.departmentName(plan.getDeptId()), itemCount, plan.getSubmitAt(), plan.getApproveAt(),
                plan.getApprovalComment());
    }

    private ParentOptionVO parentOption(BizMonthPlan plan, BizMonthPlanItem item) {
        return parentOption(plan, item, 0);
    }

    private ParentOptionVO parentOption(BizMonthPlan plan, BizMonthPlanItem item, int existingWeekPlanCount) {
        return new ParentOptionVO(item.getId(), plan.getId(), plan.getTitle(), plan.getPlanMonth(), item.getTaskType(),
                item.getPerformanceWeight(), item.getTaskName(), item.getDeadline(), item.getStatus(), existingWeekPlanCount);
    }

    private List<BizWeekPlanItem> items(Long weekPlanId) {
        return weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getDeleted, 0)
                .eq(BizWeekPlanItem::getWeekPlanId, weekPlanId)
                .orderByAsc(BizWeekPlanItem::getSortNo)
                .orderByAsc(BizWeekPlanItem::getId));
    }

    private BizWeekPlan requirePlan(Long id) {
        BizWeekPlan plan = weekPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "周计划不存在");
        }
        return plan;
    }

    private BizWeekPlan requirePlanForUpdate(Long id) {
        BizWeekPlan plan = weekPlanMapper.selectForUpdateById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "周计划不存在");
        }
        return plan;
    }

    private void requireOwner(AuthUser user, BizWeekPlan plan) {
        if (!user.userId().equals(plan.getOwnerUserId())) {
            throw new BizException(403, "只能操作本人的周计划");
        }
    }

    private void requireDirectLeader(AuthUser user, BizWeekPlan plan) {
        Long directLeaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        boolean superAdmin = user.roles() != null && user.roles().contains("SUPER_ADMIN");
        if (!superAdmin && !user.userId().equals(directLeaderId)) {
            throw new BizException(403, "只有该员工的直属领导可以审批周计划");
        }
        if (user.userId().equals(plan.getOwnerUserId())) {
            throw new BizException(403, "不能审批自己的周计划");
        }
    }

    private void requireVersion(BizWeekPlan plan, Integer versionNo) {
        if (versionNo == null || !versionNo.equals(plan.getVersionNo())) {
            throw new BizException(409, "周计划数据已变化，请刷新后重试");
        }
    }

    private LocalDate requireWeekStart(LocalDate weekStart) {
        if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BizException(422, "周计划开始日期必须是周一");
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate currentMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        if (weekStart.isBefore(currentMonday)) {
            throw new BizException(422, "周计划只能填写当前自然周及以后的周次");
        }
        return weekStart;
    }

    private void ensureNoDuplicate(Long ownerId, LocalDate weekStart, Long excludeId) {
        Long count = weekPlanMapper.selectCount(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .eq(BizWeekPlan::getOwnerUserId, ownerId)
                .eq(BizWeekPlan::getWeekStart, weekStart)
                .ne(excludeId != null, BizWeekPlan::getId, excludeId));
        if (count > 0) {
            throw new BizException(409, "该自然周已存在周计划，请打开原计划继续编辑");
        }
    }

    private String normalizeStatus(String status, Set<String> allowed) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(422, "不支持的周计划状态");
        }
        return normalized;
    }

    private String weekTitle(LocalDate weekStart) {
        return weekStart + " 至 " + weekStart.plusDays(6) + " 周计划";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void createApprovalTodo(BizWeekPlan plan, Long leaderId, AuthUser submitter, LocalDateTime now) {
        Long existing = todoMapper.selectCount(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, leaderId)
                .eq(BizTodo::getObjectType, "WEEK_PLAN")
                .eq(BizTodo::getObjectId, String.valueOf(plan.getId()))
                .ne(BizTodo::getStatus, "DONE"));
        if (existing > 0) {
            return;
        }
        SysUser receiver = dataScopeService.requireUser(leaderId);
        BizTodo todo = new BizTodo();
        todo.setSceneCode("WEEK_PLAN_APPROVAL");
        todo.setTitle("周计划待审批");
        todo.setTriggerText(submitter.realName() + "提交了" + plan.getTitle());
        todo.setReceiverId(leaderId);
        todo.setReceiverName(receiver.getRealName());
        todo.setObjectType("WEEK_PLAN");
        todo.setObjectId(String.valueOf(plan.getId()));
        todo.setDueAt(plan.getWeekStart().atTime(18, 0));
        todo.setRequirementText("审批通过或驳回员工周计划");
        todo.setImpactText("审批结果将提供给部门负责人只读查看");
        todo.setMessageType("TODO");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint("/leader/week-plan-approval?id=" + plan.getId());
        todo.setDeptId(plan.getDeptId());
        todo.setCreatedBy(submitter.userId());
        todo.setUpdatedBy(submitter.userId());
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }

    private void completeTodos(Long weekPlanId) {
        List<BizTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, "WEEK_PLAN")
                .eq(BizTodo::getObjectId, String.valueOf(weekPlanId))
                .ne(BizTodo::getStatus, "DONE"));
        for (BizTodo todo : todos) {
            todo.setStatus("DONE");
            todo.setUpdatedAt(LocalDateTime.now());
            todoMapper.updateById(todo);
        }
    }

    private void notifyReadApproverOfWithdrawal(AuthUser user, BizWeekPlan plan) {
        List<BizTodo> readTodos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, "WEEK_PLAN")
                .eq(BizTodo::getObjectId, String.valueOf(plan.getId()))
                .eq(BizTodo::getStatus, "READ"));
        for (BizTodo todo : readTodos) {
            messageService.createNotice(todo.getReceiverId(), "WORKFLOW_WITHDRAWN", "周计划已撤回",
                    user.realName() + "撤回了待审批周计划“" + plan.getTitle() + "”。",
                    "WEEK_PLAN_WITHDRAWN", String.valueOf(plan.getId()), "/leader/week-plan-approval",
                    plan.getDeptId(), user.userId());
        }
    }

    private void createResultNotification(BizWeekPlan plan, AuthUser approver, LocalDateTime now) {
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        BizTodo todo = new BizTodo();
        todo.setSceneCode("WEEK_PLAN_APPROVAL_RESULT");
        todo.setTitle(APPROVED.equals(plan.getStatus()) ? "周计划审批通过" : "周计划审批驳回");
        todo.setTriggerText(approver.realName() + "已处理" + plan.getTitle());
        todo.setReceiverId(owner.getId());
        todo.setReceiverName(owner.getRealName());
        todo.setObjectType("WEEK_PLAN_RESULT");
        todo.setObjectId(String.valueOf(plan.getId()));
        todo.setRequirementText(REJECTED.equals(plan.getStatus()) ? "查看原因并修改后重新提交" : "按审批后的周计划执行");
        todo.setImpactText("审批结果已进入部门负责人只读台账");
        todo.setMessageType("NOTICE");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint("/employee/week-plans/" + plan.getId());
        todo.setDeptId(plan.getDeptId());
        todo.setCreatedBy(approver.userId());
        todo.setUpdatedBy(approver.userId());
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }
}
