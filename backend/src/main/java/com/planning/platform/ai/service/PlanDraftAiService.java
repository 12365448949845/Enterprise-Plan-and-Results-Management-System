package com.planning.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.model.PlanDraftAiModels;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.planning.domain.*;
import com.planning.platform.planning.mapper.*;
import com.planning.platform.system.service.WorkdayCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanDraftAiService {

    private final AiRepository repository;
    private final AiInvocationService invocationService;
    private final AiOutputValidator outputValidator;
    private final AiRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizWeekPlanMapper weekPlanMapper;
    private final BizWeekPlanItemMapper weekPlanItemMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final WorkdayCalendarService workdayCalendarService;
    private final PerformanceDataScopeService dataScopeService;

    public PlanDraftAiModels.ContextResponse weekContext(AuthUser user, LocalDate weekStart) {
        requireMonday(weekStart);
        return response(user, AiModels.WEEK_PLAN_DRAFT, buildWeekBundle(user, weekStart, null));
    }

    public PlanDraftAiModels.WeekDraft generateWeek(AuthUser user, PlanDraftAiModels.WeekGenerateRequest request) {
        requireMonday(request.weekStart());
        Bundle bundle = buildWeekBundle(user, request.weekStart(), context -> {
            context.put("intentText", request.intentText());
            context.set("currentForm", objectMapper.valueToTree(request.currentForm()));
        });
        return invokeWeek(user, request.requestId(), AiModels.WEEK_PLAN_DRAFT, request.weekStart(), bundle);
    }

    public PlanDraftAiModels.WeekDraft adjustWeek(AuthUser user, PlanDraftAiModels.WeekAdjustRequest request) {
        requireMonday(request.weekStart());
        Bundle bundle = buildWeekBundle(user, request.weekStart(), context -> {
            context.set("draft", objectMapper.valueToTree(request.draft()));
            context.put("instruction", request.instruction());
            if (request.targetItemIndex() != null) context.put("targetItemIndex", request.targetItemIndex());
        });
        return invokeWeek(user, request.requestId(), AiModels.WEEK_PLAN_ADJUST, request.weekStart(), bundle);
    }

    public PlanDraftAiModels.ContextResponse dayContext(AuthUser user, LocalDate planDate) {
        requireDate(planDate);
        return response(user, AiModels.DAY_PLAN_DRAFT, buildDayBundle(user, planDate, null));
    }

    public PlanDraftAiModels.DayDraft generateDay(AuthUser user, PlanDraftAiModels.DayGenerateRequest request) {
        requireDate(request.planDate());
        Bundle bundle = buildDayBundle(user, request.planDate(), context -> {
            context.put("intentText", request.intentText());
            context.set("currentForm", objectMapper.valueToTree(request.currentForm()));
        });
        return invokeDay(user, request.requestId(), AiModels.DAY_PLAN_DRAFT, bundle);
    }

    public PlanDraftAiModels.DayDraft adjustDay(AuthUser user, PlanDraftAiModels.DayAdjustRequest request) {
        requireDate(request.planDate());
        Bundle bundle = buildDayBundle(user, request.planDate(), context -> {
            context.set("draft", objectMapper.valueToTree(request.draft()));
            context.put("instruction", request.instruction());
        });
        return invokeDay(user, request.requestId(), AiModels.DAY_PLAN_ADJUST, bundle);
    }

    public void recordAction(AuthUser user, String suggestionId, AiModels.SuggestionActionRequest request) {
        repository.saveAction(user, suggestionId, request);
    }

    private PlanDraftAiModels.WeekDraft invokeWeek(AuthUser user, String requestId, String scene,
                                                    LocalDate weekStart, Bundle bundle) {
        return invocationService.invoke(user, requestId, scene, "WEEK_PLAN", null, bundle.context(),
                (content, suggestionId) -> outputValidator.validateWeekDraft(content, suggestionId,
                        weekStart, bundle.allowedParentIds(), bundle.missing()),
                PlanDraftAiModels.WeekDraft.class, UnaryOperator.identity());
    }

    private PlanDraftAiModels.DayDraft invokeDay(AuthUser user, String requestId, String scene, Bundle bundle) {
        return invocationService.invoke(user, requestId, scene, "DAY_PLAN", null, bundle.context(),
                (content, suggestionId) -> outputValidator.validateDayDraft(content, suggestionId,
                        bundle.allowedParentIds(), bundle.missing()),
                PlanDraftAiModels.DayDraft.class, UnaryOperator.identity());
    }

    private Bundle buildWeekBundle(AuthUser user, LocalDate weekStart,
                                   java.util.function.Consumer<ObjectNode> extra) {
        LocalDate weekEnd = weekStart.plusDays(6);
        ParentData parents = parents(user);
        ObjectNode context = baseContext(user);
        context.put("weekStart", weekStart.toString());
        context.put("weekEnd", weekEnd.toString());
        context.set("parentOptions", objectMapper.valueToTree(parents.options()));
        List<PlanDraftAiModels.RelatedWeekItem> related = weekItems(user, weekStart, weekEnd, parents.ids());
        context.set("relatedWeekItems", objectMapper.valueToTree(related));
        if (extra != null) extra.accept(context);
        List<String> missing = parents.ids().isEmpty() ? List.of("未找到当前员工已审批的月计划事项") : List.of();
        return new Bundle(context, parents.ids(), missing, parents.options(), related);
    }

    private Bundle buildDayBundle(AuthUser user, LocalDate planDate,
                                  java.util.function.Consumer<ObjectNode> extra) {
        ParentData parents = parents(user);
        LocalDate monday = planDate.minusDays(planDate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        List<PlanDraftAiModels.RelatedWeekItem> related = weekItems(user, monday, monday.plusDays(6), parents.ids());
        ObjectNode context = baseContext(user);
        context.put("planDate", planDate.toString());
        context.set("workdayRule", objectMapper.valueToTree(workdayCalendarService.resolve(planDate)));
        context.set("parentOptions", objectMapper.valueToTree(parents.options()));
        context.set("relatedWeekItems", objectMapper.valueToTree(related));
        List<BizDayPlan> recent = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getOwnerUserId, user.userId()).eq(BizDayPlan::getDeleted, 0)
                .between(BizDayPlan::getPlanDate, planDate.minusDays(14), planDate.minusDays(1)));
        ArrayNode recentJson = context.putArray("recentDayPlans");
        for (BizDayPlan plan : recent) recentJson.addObject().put("planDate", String.valueOf(plan.getPlanDate()))
                .put("content", defaultText(plan.getContent())).put("remark", defaultText(plan.getRemark()));
        if (extra != null) extra.accept(context);
        List<String> missing = new ArrayList<>();
        if (parents.ids().isEmpty()) missing.add("未找到当前员工已审批的月计划事项");
        if (related.isEmpty()) missing.add("未找到所选日期对应的周计划任务");
        return new Bundle(context, parents.ids(), List.copyOf(missing), parents.options(), related);
    }

    private ParentData parents(AuthUser user) {
        List<BizMonthPlan> plans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getOwnerUserId, user.userId()).eq(BizMonthPlan::getStatus, "APPROVED")
                .eq(BizMonthPlan::getDeleted, 0));
        Set<Long> planIds = plans.stream().filter(p -> Objects.equals(p.getOwnerUserId(), user.userId()))
                .filter(p -> "APPROVED".equals(p.getStatus()) && !Integer.valueOf(1).equals(p.getDeleted()))
                .map(BizMonthPlan::getId).collect(Collectors.toSet());
        if (planIds.isEmpty()) return new ParentData(List.of(), Set.of());
        Map<Long, String> months = plans.stream().filter(p -> planIds.contains(p.getId()))
                .collect(Collectors.toMap(BizMonthPlan::getId, BizMonthPlan::getPlanMonth, (a, b) -> a));
        List<PlanDraftAiModels.ParentOption> options = monthPlanItemMapper.selectList(
                        new LambdaQueryWrapper<BizMonthPlanItem>().in(BizMonthPlanItem::getMonthPlanId, planIds)
                                .eq(BizMonthPlanItem::getDeleted, 0)).stream()
                .filter(i -> planIds.contains(i.getMonthPlanId()) && !Integer.valueOf(1).equals(i.getDeleted()))
                .map(i -> new PlanDraftAiModels.ParentOption(i.getId(), months.get(i.getMonthPlanId()),
                        i.getTaskName(), i.getTaskContent(), i.getDeliverable())).toList();
        return new ParentData(options, options.stream().map(PlanDraftAiModels.ParentOption::id).collect(Collectors.toSet()));
    }

    private List<PlanDraftAiModels.RelatedWeekItem> weekItems(AuthUser user, LocalDate start, LocalDate end,
                                                               Set<Long> allowedIds) {
        List<BizWeekPlan> plans = weekPlanMapper.selectList(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getOwnerUserId, user.userId()).eq(BizWeekPlan::getDeleted, 0)
                .le(BizWeekPlan::getWeekStart, end).ge(BizWeekPlan::getWeekEnd, start));
        Set<Long> ids = plans.stream().filter(p -> Objects.equals(p.getOwnerUserId(), user.userId()))
                .filter(p -> !Integer.valueOf(1).equals(p.getDeleted()) && !p.getWeekStart().isAfter(end)
                        && !p.getWeekEnd().isBefore(start)).map(BizWeekPlan::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) return List.of();
        return weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                        .in(BizWeekPlanItem::getWeekPlanId, ids).eq(BizWeekPlanItem::getDeleted, 0)).stream()
                .filter(i -> ids.contains(i.getWeekPlanId()) && allowedIds.contains(i.getMonthPlanItemId())
                        && !Integer.valueOf(1).equals(i.getDeleted()))
                .map(i -> new PlanDraftAiModels.RelatedWeekItem(i.getId(), i.getMonthPlanItemId(), i.getContent(),
                        i.getDeliverable(), i.getPlannedFinishDate())).toList();
    }

    private PlanDraftAiModels.ContextResponse response(AuthUser user, String scene, Bundle bundle) {
        Optional<AiRepository.ModelConfig> value = repository.activeConfig();
        boolean enabled = value.filter(c -> c.globalEnabled() && c.sceneEnabled(scene)).isPresent();
        Map<String, Integer> remaining = new LinkedHashMap<>();
        value.ifPresent(c -> remaining.put(scene, rateLimitService.remaining(user.userId(), scene, c.limitFor(scene))));
        return new PlanDraftAiModels.ContextResponse(enabled, value.map(AiRepository.ModelConfig::providerCode).orElse(""),
                value.map(AiRepository.ModelConfig::modelName).orElse(""),
                bundle.missing().isEmpty() ? List.of("已加载月计划与计划周期上下文") : List.of(),
                bundle.missing(), bundle.options(), bundle.related(), remaining, AiModels.NOTICE);
    }

    private ObjectNode baseContext(AuthUser user) {
        ObjectNode context = objectMapper.createObjectNode();
        context.put("employee", user.realName());
        context.put("department", dataScopeService.departmentName(user.deptId()));
        return context;
    }

    private void requireMonday(LocalDate date) {
        requireDate(date);
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) throw new BizException(422, "weekStart 必须为周一");
    }

    private void requireDate(LocalDate date) {
        if (date == null) throw new BizException(422, "计划日期不能为空");
    }

    private String defaultText(String value) { return value == null ? "" : value; }

    private record ParentData(List<PlanDraftAiModels.ParentOption> options, Set<Long> ids) {}
    private record Bundle(ObjectNode context, Set<Long> allowedParentIds, List<String> missing,
                          List<PlanDraftAiModels.ParentOption> options,
                          List<PlanDraftAiModels.RelatedWeekItem> related) {}
}
