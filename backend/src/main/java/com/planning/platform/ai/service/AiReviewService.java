package com.planning.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.planning.platform.ai.domain.BizAiReview;
import com.planning.platform.ai.mapper.BizAiReviewMapper;
import com.planning.platform.ai.model.AiReviewModels.AcceptanceCoverage;
import com.planning.platform.ai.model.AiReviewModels.AnalysisCallContext;
import com.planning.platform.ai.model.AiReviewModels.AnalysisDimension;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.CapabilityVO;
import com.planning.platform.ai.model.AiReviewModels.Issue;
import com.planning.platform.ai.model.AiReviewModels.ModelAnalysis;
import com.planning.platform.ai.model.AiReviewModels.ReviewResult;
import com.planning.platform.ai.model.AiReviewModels.ReviewVO;
import com.planning.platform.ai.model.ExtraTaskPreviewReq;
import com.planning.platform.ai.service.EvidenceDocumentService.EvidenceDocument;
import com.planning.platform.ai.service.AiCompletionCalculator.CompletionAssessment;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.domain.BizAcceptanceStandard;
import com.planning.platform.performance.domain.BizDeliverableTemplate;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.domain.BizWeekPlan;
import com.planning.platform.planning.domain.BizWeekPlanItem;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.mapper.BizWeekPlanItemMapper;
import com.planning.platform.planning.mapper.BizWeekPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import com.planning.platform.system.service.WorkdayCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiReviewService {

    public static final String MONTH_PLAN = "MONTH_PLAN";
    public static final String WEEK_PLAN = "WEEK_PLAN";
    public static final String DAY_PLAN = "DAY_PLAN";
    public static final String EXTRA_TASK = "EXTRA_TASK";
    public static final String RESULT = "RESULT";
    private static final Set<String> PLAN_TYPES = Set.of(MONTH_PLAN, WEEK_PLAN, DAY_PLAN, EXTRA_TASK);
    private static final long RECENT_FAILURE_REUSE_SECONDS = 120L;

    private final BizAiReviewMapper reviewMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizWeekPlanMapper weekPlanMapper;
    private final BizWeekPlanItemMapper weekPlanItemMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper resultEvidenceMapper;
    private final BizDeliverableTemplateMapper templateMapper;
    private final BizAcceptanceStandardMapper acceptanceStandardMapper;
    private final PerformanceDataScopeService dataScopeService;
    private final WorkdayCalendarService workdayCalendarService;
    private final EvidenceDocumentService evidenceDocumentService;
    private final AiCompletionCalculator completionCalculator;
    private final AiGroundingService groundingService;
    private final AiRateLimitService rateLimitService;
    private final QwenAiClient qwenAiClient;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final Map<String, CompletableFuture<ReviewVO>> inFlightReviews = new ConcurrentHashMap<>();

    public CapabilityVO capability(AuthUser user) {
        boolean globallyEnabled = qwenAiClient.available();
        boolean enabled = globallyEnabled && qwenAiClient.permitted(user.userId(), user.deptId());
        return new CapabilityVO(
                enabled,
                enabled ? "QWEN" : "RULE_ONLY",
                qwenAiClient.provider(),
                qwenAiClient.model(),
                qwenAiClient.promptVersion(),
                enabled
                        ? "千问语义检查已启用。提交前会结合系统规则、计划上下级关系和证据原文生成可追溯报告。"
                        : globallyEnabled
                        ? "千问语义检查已启用，但当前账号不在灰度范围内；系统仍会执行确定性规则检查。"
                        : "千问尚未启用，当前仍会执行必填项、日期、权重和文件完整性等系统规则检查。"
        );
    }

    public ReviewVO checkPlan(AuthUser user, String rawBizType, Long bizId) {
        String bizType = normalizePlanType(rawBizType);
        ContextBundle context = buildPlanContext(user, bizType, bizId);
        ReviewVO result = executeReview(user, context, "EMPLOYEE_CHECK");
        auditLogService.success(user, "AI_REVIEW_CHECK", bizType, bizId,
                json(Map.of("reviewId", result.id(), "status", result.status(), "risk", result.overallRisk())));
        return result;
    }

    public ReviewVO ensurePlanReview(AuthUser user, String rawBizType, Long bizId) {
        String bizType = normalizePlanType(rawBizType);
        ContextBundle context = buildPlanContext(user, bizType, bizId);
        boolean modelEnabledForUser = qwenAiClient.available()
                && qwenAiClient.permitted(user.userId(), context.deptId());
        BizAiReview latest = latestByHash(context.ownerUserId(), bizType, context.contentHash(), modelEnabledForUser);
        if (latest != null && Objects.equals(latest.getBizId(), bizId)) {
            return toVO(latest, false);
        }
        return executeReview(user, context, "SUBMIT_AUTO_CHECK");
    }

    public ReviewVO attachOrRunPlanReview(AuthUser user, String rawBizType, Long bizId, Long previewReviewId) {
        String bizType = normalizePlanType(rawBizType);
        ContextBundle context = buildPlanContext(user, bizType, bizId);
        boolean modelEnabledForUser = qwenAiClient.available()
                && qwenAiClient.permitted(user.userId(), context.deptId());
        if (previewReviewId != null) {
            BizAiReview preview = reviewMapper.selectById(previewReviewId);
            if (preview != null && Integer.valueOf(0).equals(preview.getDeleted())
                    && bizType.equals(preview.getBizType())
                    && Objects.equals(preview.getOwnerUserId(), user.userId())
                    && Objects.equals(preview.getBizId(), 0L)
                    && Objects.equals(preview.getContentHash(), context.contentHash())
                    && reusableWithCurrentConfiguration(preview, modelEnabledForUser)
                    && preview.getCreatedAt() != null
                    && preview.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(30))) {
                preview.setBizId(bizId);
                preview.setBizVersion(context.bizVersion());
                preview.setTriggerSource("PLAN_SUBMIT_REUSE");
                preview.setUpdatedAt(LocalDateTime.now());
                reviewMapper.updateById(preview);
                return toVO(preview, false);
            }
        }
        return executeReview(user, context, "SUBMIT_AUTO_CHECK");
    }

    public ReviewVO previewResult(AuthUser user, Long monthPlanId, Long monthPlanItemId,
                                  Integer completionRate, String description, MultipartFile file) {
        EvidenceDocument evidence = evidenceDocumentService.inspect(file);
        ContextBundle context = buildResultContext(user, 0L, monthPlanId, monthPlanItemId,
                completionRate, description, evidence);
        ReviewVO result = executeReview(user, context, "RESULT_PREVIEW");
        auditLogService.success(user, "AI_RESULT_PREVIEW", RESULT, result.id(),
                json(Map.of("reviewId", result.id(), "risk", result.overallRisk(), "fileChecksum", evidence.checksum())));
        return result;
    }

    public ReviewVO previewExtraTask(AuthUser user, Long monthPlanId, ExtraTaskPreviewReq request) {
        BizMonthPlan plan = requireMonthPlan(monthPlanId);
        requireOwner(user, plan.getOwnerUserId());
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setMonthPlanId(monthPlanId);
        item.setTaskType("EXTRA");
        item.setTaskName(request.getTaskName());
        item.setTaskContent(request.getTaskContent());
        item.setDeliverable(request.getDeliverable());
        item.setDeadline(request.getDeadline());
        item.setPerformanceWeight(request.getPerformanceWeight());
        List<BizMonthPlanItem> siblings = monthItems(plan.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approvedMonthPlan", mapMonthPlan(plan));
        data.put("extraTask", mapMonthItemContent(item));
        data.put("existingMonthTasks", siblings.stream().map(this::mapMonthItem).toList());
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "MONTH_PLAN"));
        data.put("instruction", "重点判断额外任务是否与原月计划重复、是否确属新增工作，以及新增工作量、期限、交付物和权重是否合理。");
        ContextBundle context = bundle(EXTRA_TASK, 0L, "PREVIEW", plan.getOwnerUserId(), plan.getDeptId(),
                data, extraTaskRules(), monthRuleIssues(plan, List.of(item), true), null);
        return executeReview(user, context, "EXTRA_TASK_PREVIEW");
    }

    public ReviewVO attachOrRunResultReview(AuthUser user, Long resultId, Long previewReviewId,
                                            Long monthPlanId, Long monthPlanItemId, Integer completionRate,
                                            String description, EvidenceDocument evidence) {
        ContextBundle context = buildResultContext(user, resultId, monthPlanId, monthPlanItemId,
                completionRate, description, evidence);
        boolean modelEnabledForUser = qwenAiClient.available()
                && qwenAiClient.permitted(user.userId(), context.deptId());
        if (previewReviewId != null) {
            BizAiReview preview = reviewMapper.selectById(previewReviewId);
            if (preview != null && Integer.valueOf(0).equals(preview.getDeleted())
                    && RESULT.equals(preview.getBizType())
                    && Objects.equals(preview.getOwnerUserId(), user.userId())
                    && Objects.equals(preview.getBizId(), 0L)
                    && Objects.equals(preview.getContentHash(), context.contentHash())
                    && reusableWithCurrentConfiguration(preview, modelEnabledForUser)
                    && preview.getCreatedAt() != null
                    && preview.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(30))) {
                preview.setBizId(resultId);
                preview.setBizVersion(context.bizVersion());
                preview.setTriggerSource("RESULT_SUBMIT_REUSE");
                preview.setUpdatedAt(LocalDateTime.now());
                reviewMapper.updateById(preview);
                return toVO(preview, false);
            }
        }
        return executeReview(user, context, "RESULT_SUBMIT_AUTO_CHECK");
    }

    public ReviewVO latestForViewer(AuthUser user, String rawBizType, Long bizId) {
        String bizType = normalizeAnyType(rawBizType);
        BizAiReview review = reviewMapper.selectOne(new LambdaQueryWrapper<BizAiReview>()
                .eq(BizAiReview::getDeleted, 0)
                .eq(BizAiReview::getBizType, bizType)
                .eq(BizAiReview::getBizId, bizId)
                .orderByDesc(BizAiReview::getCreatedAt)
                .orderByDesc(BizAiReview::getId)
                .last("LIMIT 1"));
        if (review == null) {
            return null;
        }
        requireViewer(user, review.getOwnerUserId());
        return toVO(review, isStale(review));
    }

    public ReviewVO latestForOwner(Long ownerUserId, String rawBizType, Long bizId) {
        String bizType = normalizeAnyType(rawBizType);
        BizAiReview review = reviewMapper.selectOne(new LambdaQueryWrapper<BizAiReview>()
                .eq(BizAiReview::getDeleted, 0)
                .eq(BizAiReview::getOwnerUserId, ownerUserId)
                .eq(BizAiReview::getBizType, bizType)
                .eq(BizAiReview::getBizId, bizId)
                .orderByDesc(BizAiReview::getCreatedAt)
                .orderByDesc(BizAiReview::getId)
                .last("LIMIT 1"));
        return review == null ? null : toVO(review, isStale(review));
    }

    private ReviewVO executeReview(AuthUser user, ContextBundle context, String triggerSource) {
        String key = String.join("|", context.bizType(), String.valueOf(context.bizId()),
                String.valueOf(context.ownerUserId()), context.contentHash(), qwenAiClient.provider(),
                qwenAiClient.model(), qwenAiClient.promptVersion());
        CompletableFuture<ReviewVO> created = new CompletableFuture<>();
        CompletableFuture<ReviewVO> existing = inFlightReviews.putIfAbsent(key, created);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("AI检查并发任务失败", cause);
            }
        }
        try {
            ReviewVO result = executeReviewNow(user, context, triggerSource);
            created.complete(result);
            return result;
        } catch (RuntimeException ex) {
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlightReviews.remove(key, created);
        }
    }

    private ReviewVO executeReviewNow(AuthUser user, ContextBundle context, String triggerSource) {
        List<Issue> issues = new ArrayList<>(context.ruleIssues());
        ModelAnalysis modelAnalysis = new ModelAnalysis("", List.of(), List.of(), List.of());
        boolean globallyAvailable = qwenAiClient.available();
        boolean modelAvailable = globallyAvailable && qwenAiClient.permitted(user.userId(), context.deptId());
        String provider = qwenAiClient.provider();
        String model = qwenAiClient.model();
        String promptVersion = qwenAiClient.promptVersion();
        String status = modelAvailable ? "SUCCESS" : "RULE_ONLY";
        String errorMessage = globallyAvailable && !modelAvailable ? "当前账号不在AI检查灰度范围内" : null;
        if (modelAvailable) {
            try {
                rateLimitService.consume(user.userId(), AiRateLimitService.AI_REVIEW_CHECK,
                        qwenAiClient.checkDailyLimit());
                AnalysisRequest request = groundingService.prepare(
                        context.bizType(), context.rules(), context.businessData());
                AnalysisCallContext callContext = new AnalysisCallContext(user.userId(), context.deptId(),
                        context.bizId(), context.contentHash());
                modelAnalysis = groundingService.validate(request, qwenAiClient.analyze(request, callContext));
                issues.addAll(modelAnalysis.issues());
            } catch (Exception ex) {
                status = "MODEL_FAILED";
                errorMessage = truncate(ex.getMessage(), 1000);
            }
        }
        List<AnalysisDimension> dimensions = "SUCCESS".equals(status)
                ? modelAnalysis.analysisDimensions()
                : List.of();
        issues = deduplicate(issues);
        List<AcceptanceCoverage> coverage = RESULT.equals(context.bizType())
                ? modelAnalysis.acceptanceCoverage() : List.of();
        CompletionAssessment completion = RESULT.equals(context.bizType())
                ? completionCalculator.assess(context.declaredCompletionRate(), coverage, issues)
                : completionCalculator.withoutCompletion(issues);
        issues = deduplicate(completion.issues());
        String overallRisk = overallRisk(issues);
        String summary = summary(status, modelAnalysis.summary(), issues, dimensions, completion);
        ReviewResult result = new ReviewResult(overallRisk, summary, issues, dimensions, coverage,
                completion.suggestedMin(), completion.suggestedMax(), completion.evidenceStatus(),
                context.declaredCompletionRate(), completion.calculationBasis());

        BizAiReview review = new BizAiReview();
        review.setBizType(context.bizType());
        review.setBizId(context.bizId());
        review.setBizVersion(context.bizVersion());
        review.setContentHash(context.contentHash());
        review.setOwnerUserId(context.ownerUserId());
        review.setDeptId(context.deptId());
        review.setTriggerSource(triggerSource);
        review.setReviewStatus(status);
        review.setOverallRisk(overallRisk);
        review.setProvider(provider);
        review.setModelName(model);
        review.setPromptVersion(promptVersion);
        review.setResultJson(json(result));
        review.setErrorMessage(errorMessage);
        review.setCreatedBy(user.userId());
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        review.setDeleted(0);
        reviewMapper.insert(review);
        return toVO(review, false);
    }

    private ContextBundle buildPlanContext(AuthUser user, String bizType, Long bizId) {
        ContextBundle context = buildPlanContext(bizType, bizId);
        requireOwner(user, context.ownerUserId());
        return context;
    }

    private ContextBundle buildPlanContext(String bizType, Long bizId) {
        return switch (bizType) {
            case MONTH_PLAN -> monthPlanContext(bizId);
            case WEEK_PLAN -> weekPlanContext(bizId);
            case DAY_PLAN -> dayPlanContext(bizId);
            case EXTRA_TASK -> extraTaskContext(bizId);
            default -> throw new BizException(422, "不支持的AI检查对象");
        };
    }

    private ContextBundle monthPlanContext(Long id) {
        BizMonthPlan plan = requireMonthPlan(id);
        List<BizMonthPlanItem> items = monthItems(plan.getId());
        List<Issue> ruleIssues = monthRuleIssues(plan, items, false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", mapMonthPlan(plan));
        data.put("items", items.stream().map(this::mapMonthItem).toList());
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "MONTH_PLAN"));
        data.put("instruction", "检查整份月计划的任务是否具体、任务内容与交付物是否一致、交付物是否可核验，以及期限、权重和任务之间是否存在冲突或重复。");
        return bundle(MONTH_PLAN, id, String.valueOf(plan.getVersionNo()), plan.getOwnerUserId(), plan.getDeptId(),
                data, planRules(), ruleIssues, null);
    }

    private ContextBundle extraTaskContext(Long itemId) {
        BizMonthPlanItem item = requireMonthItem(itemId);
        if (!"EXTRA".equals(item.getTaskType())) {
            throw new BizException(422, "所选事项不是额外任务");
        }
        BizMonthPlan plan = requireMonthPlan(item.getMonthPlanId());
        List<BizMonthPlanItem> siblings = monthItems(plan.getId());
        List<Issue> ruleIssues = monthRuleIssues(plan, List.of(item), true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approvedMonthPlan", mapMonthPlan(plan));
        data.put("extraTask", mapMonthItemContent(item));
        data.put("existingMonthTasks", siblings.stream().filter(value -> !value.getId().equals(itemId))
                .map(this::mapMonthItem).toList());
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "MONTH_PLAN"));
        data.put("instruction", "重点判断额外任务是否与原月计划重复、是否确属新增工作，以及任务范围、期限、交付物和权重是否合理。");
        return bundle(EXTRA_TASK, itemId, String.valueOf(item.getVersionNo()), plan.getOwnerUserId(), plan.getDeptId(),
                data, extraTaskRules(), ruleIssues, null);
    }

    private ContextBundle weekPlanContext(Long id) {
        BizWeekPlan plan = weekPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "周计划不存在");
        }
        List<BizWeekPlanItem> items = weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getDeleted, 0)
                .eq(BizWeekPlanItem::getWeekPlanId, id)
                .orderByAsc(BizWeekPlanItem::getSortNo));
        Set<Long> parentIds = items.stream()
                .map(BizWeekPlanItem::getMonthPlanItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, BizMonthPlanItem> parents = parentIds.isEmpty() ? Map.of()
                : monthPlanItemMapper.selectBatchIds(parentIds).stream()
                .filter(value -> !Integer.valueOf(1).equals(value.getDeleted()))
                .collect(Collectors.toMap(BizMonthPlanItem::getId, value -> value));
        List<Issue> ruleIssues = weekRuleIssues(plan, items, parents);
        List<Map<String, Object>> itemData = new ArrayList<>();
        for (BizWeekPlanItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekItem", mapWeekItem(item));
            row.put("parentMonthItem", mapMonthItem(parents.get(item.getMonthPlanItemId())));
            itemData.add(row);
        }
        List<Map<String, Object>> relatedWeeks = relatedWeekContexts(plan, items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekPlan", mapWeekPlan(plan));
        data.put("itemsWithParents", itemData);
        data.put("otherWeeksForSameMonthItems", relatedWeeks);
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "MONTH_PLAN"));
        data.put("instruction", "逐项对照父级月计划，检查周计划是否构成合理拆解、是否重复、是否在一周内可完成，以及阶段交付物是否支撑父级任务和最终交付物。");
        return bundle(WEEK_PLAN, id, String.valueOf(plan.getVersionNo()), plan.getOwnerUserId(), plan.getDeptId(),
                data, weekRules(), ruleIssues, null);
    }

    private ContextBundle dayPlanContext(Long id) {
        BizDayPlan plan = dayPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划不存在");
        }
        BizMonthPlanItem parentItem = plan.getMonthPlanItemId() == null ? null : monthPlanItemMapper.selectById(plan.getMonthPlanItemId());
        BizMonthPlan parentPlan = parentItem == null ? null : monthPlanMapper.selectById(parentItem.getMonthPlanId());
        BizWeekPlanItem weekItem = inferWeekItem(plan);
        WorkdayCalendarService.CalendarDay calendarDay = workdayCalendarService.resolve(plan.getPlanDate());
        List<Issue> ruleIssues = dayRuleIssues(plan, parentItem, calendarDay);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dayPlan", mapDayPlan(plan));
        data.put("workdayRule", mapCalendar(calendarDay));
        data.put("parentMonthPlan", mapMonthPlan(parentPlan));
        data.put("parentMonthItem", mapMonthItem(parentItem));
        data.put("correspondingWeekItem", mapWeekItem(weekItem));
        data.put("recentDayPlans", recentDayPlans(plan).stream().map(this::mapDayPlan).toList());
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "DAY_PLAN"));
        data.put("instruction", "判断日计划内容是否具体可执行、能否支撑对应周/月计划、是否适合一天完成，以及是否与近期日计划机械重复。日计划没有独立交付物字段，不得把这一点当作硬性缺失。");
        return bundle(DAY_PLAN, id, versionOf(plan.getUpdatedAt(), plan.getSubmitAt()), plan.getOwnerUserId(), plan.getDeptId(),
                data, dayRules(), ruleIssues, null);
    }

    private ContextBundle buildResultContext(AuthUser user, Long bizId, Long monthPlanId, Long monthPlanItemId,
                                             Integer completionRate, String description, EvidenceDocument evidence) {
        if (completionRate == null || completionRate < 0 || completionRate > 100) {
            throw new BizException(422, "成果完成比例必须在0到100之间");
        }
        BizMonthPlan plan = requireMonthPlan(monthPlanId);
        requireOwner(user, plan.getOwnerUserId());
        BizMonthPlanItem item = monthPlanItemId == null ? null : requireMonthItem(monthPlanItemId);
        if (item != null && !Objects.equals(item.getMonthPlanId(), plan.getId())) {
            throw new BizException(422, "成果事项不属于所选月计划");
        }
        List<Map<String, Object>> acceptanceCriteria = new ArrayList<>();
        if (item != null && StringUtils.hasText(item.getDeliverable())) {
            acceptanceCriteria.add(acceptanceCriterion("DELIVERABLE_ITEM_" + item.getId(),
                    "应提供并证明交付物：" + item.getDeliverable()));
        } else {
            for (BizMonthPlanItem planItem : monthItems(plan.getId())) {
                if (StringUtils.hasText(planItem.getDeliverable())) {
                    acceptanceCriteria.add(acceptanceCriterion("DELIVERABLE_ITEM_" + planItem.getId(),
                            "应提供并证明交付物：" + planItem.getDeliverable()));
                }
            }
        }
        List<Issue> ruleIssues = new ArrayList<>();
        if (!StringUtils.hasText(description)) {
            ruleIssues.add(issue("RESULT_DESCRIPTION_MISSING", "RULE", "MEDIUM", "description",
                    "成果说明为空", "SYS_RESULT_01", "成果说明为空",
                    "缺少成果说明会降低证据与计划之间的可追溯性。", "补充本次实际完成内容、范围和未完成项。", 1D,
                    List.of("成果说明")));
        }
        if (!evidence.readableText()) {
            ruleIssues.add(issue("EVIDENCE_TEXT_UNREADABLE", "RULE", "HIGH", "file",
                    "证据无法提取可核验文字", "SYS_RESULT_02", evidence.fileName(),
                    "文件技术上可读取，但没有提取到可供AI核验的文字，可能是扫描件或仅包含图片。",
                    "请提供含可复制文字的PDF或Word，必要时补充文字版说明。", 1D,
                    List.of("证据文件:" + evidence.fileName())));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("monthPlan", mapMonthPlan(plan));
        data.put("planItem", mapMonthItem(item));
        data.put("acceptanceCriteria", acceptanceCriteria);
        data.put("result", Map.of(
                "description", defaultText(description, ""),
                "declaredCompletionRate", completionRate
        ));
        Map<String, Object> evidenceData = new LinkedHashMap<>();
        evidenceData.put("fileName", evidence.fileName());
        evidenceData.put("fileType", evidence.fileType());
        evidenceData.put("fileSize", evidence.fileSize());
        evidenceData.put("checksum", evidence.checksum());
        evidenceData.put("textTruncated", evidence.truncated());
        evidenceData.put("extractedText", evidence.extractedText());
        evidenceData.put("sameEmployeeDuplicateCount", duplicateEvidenceCount(user.userId(), evidence.checksum()));
        data.put("evidence", evidenceData);
        data.put("organizationStandards", organizationStandards(plan.getDeptId(), "RESULT"));
        data.put("instruction", "逐项对照计划交付物核验项与证据内容。每个覆盖结论必须引用证据页码、段落或Zip内路径。不要直接给完成比例，由系统根据覆盖状态计算。");
        String version = bizId == 0 ? "PREVIEW" : "RESULT-" + bizId;
        return bundle(RESULT, bizId, version, plan.getOwnerUserId(), plan.getDeptId(),
                data, resultRules(), ruleIssues, completionRate);
    }

    private List<Issue> monthRuleIssues(BizMonthPlan plan, List<BizMonthPlanItem> items, boolean extraOnly) {
        List<Issue> issues = new ArrayList<>();
        if (items.isEmpty()) {
            issues.add(blocking("PLAN_ITEM_MISSING", "items", "计划没有任务条目", "SYS_PLAN_01",
                    "计划条目为空", "至少补充一条完整任务。"));
            return issues;
        }
        BigDecimal regularWeight = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            BizMonthPlanItem item = items.get(index);
            String field = "items[" + index + "]";
            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(item.getTaskName())) missing.add("任务名称");
            if (!StringUtils.hasText(item.getTaskContent())) missing.add("任务内容");
            if (!StringUtils.hasText(item.getDeliverable())) missing.add("交付物");
            if (item.getDeadline() == null) missing.add("截止日期");
            if (!missing.isEmpty()) {
                issues.add(blocking("REQUIRED_FIELD_MISSING", field, "计划必要字段缺失", "SYS_PLAN_02",
                        String.join("、", missing), "补充缺失字段后再提交。"));
            }
            if (item.getPerformanceWeight() == null || item.getPerformanceWeight().compareTo(BigDecimal.ZERO) <= 0) {
                issues.add(blocking("PERFORMANCE_WEIGHT_INVALID", field + ".performanceWeight", "绩效权重不合法",
                        "SYS_PLAN_03", String.valueOf(item.getPerformanceWeight()), "绩效权重必须大于0。"));
            }
            if (item.getDeadline() != null && StringUtils.hasText(plan.getPlanMonth())) {
                try {
                    if (!YearMonth.from(item.getDeadline()).equals(YearMonth.parse(plan.getPlanMonth()))) {
                        issues.add(blocking("DEADLINE_OUTSIDE_MONTH", field + ".deadline", "截止日期不在计划月份内",
                                "SYS_PLAN_04", String.valueOf(item.getDeadline()), "将截止日期调整到计划月份内。"));
                    }
                } catch (Exception ignored) {
                    issues.add(blocking("PLAN_MONTH_INVALID", "planMonth", "计划月份格式不正确", "SYS_PLAN_05",
                            plan.getPlanMonth(), "使用YYYY-MM格式的计划月份。"));
                }
            }
            if (!extraOnly && !"EXTRA".equals(item.getTaskType()) && item.getPerformanceWeight() != null) {
                regularWeight = regularWeight.add(item.getPerformanceWeight());
            }
        }
        if (!extraOnly && regularWeight.compareTo(new BigDecimal("100")) != 0) {
            issues.add(blocking("REGULAR_WEIGHT_TOTAL_INVALID", "items.performanceWeight", "常规任务权重合计不等于100%",
                    "SYS_PLAN_06", regularWeight.stripTrailingZeros().toPlainString() + "%", "调整常规任务权重，使合计等于100%。"));
        }
        return issues;
    }

    private List<Issue> weekRuleIssues(BizWeekPlan plan, List<BizWeekPlanItem> items,
                                       Map<Long, BizMonthPlanItem> parents) {
        List<Issue> issues = new ArrayList<>();
        if (items.isEmpty()) {
            issues.add(blocking("WEEK_ITEM_MISSING", "items", "周计划没有任务条目", "SYS_WEEK_01",
                    "周计划条目为空", "至少补充一条周计划。"));
        }
        Set<Long> used = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            BizWeekPlanItem item = items.get(index);
            String field = "items[" + index + "]";
            if (!StringUtils.hasText(item.getContent())) {
                issues.add(blocking("WEEK_CONTENT_MISSING", field + ".content", "周计划内容为空", "SYS_WEEK_02",
                        "本周工作内容为空", "填写本周具体工作内容。"));
            }
            if (!StringUtils.hasText(item.getDeliverable())) {
                issues.add(issue("WEEK_DELIVERABLE_MISSING", "RULE", "MEDIUM", field + ".deliverable",
                        "本周交付物未说明", "SYS_WEEK_06", "本周交付物为空",
                        "缺少阶段交付物时，无法核对本周工作是否形成可确认的阶段结果。",
                        "补充本周预计形成的文档、代码、清单、数据或其他可查看结果。", 1D,
                        List.of("第" + (index + 1) + "项周计划 · 交付物")));
            }
            if (item.getMonthPlanItemId() == null || !parents.containsKey(item.getMonthPlanItemId())) {
                issues.add(blocking("WEEK_PARENT_INVALID", field + ".monthPlanItemId", "关联月计划事项无效",
                        "SYS_WEEK_03", String.valueOf(item.getMonthPlanItemId()), "重新选择有效的已审批月计划事项。"));
            } else if (!used.add(item.getMonthPlanItemId())) {
                issues.add(blocking("WEEK_PARENT_DUPLICATED", field + ".monthPlanItemId", "重复关联同一月计划事项",
                        "SYS_WEEK_04", String.valueOf(item.getMonthPlanItemId()), "合并重复条目或选择其他月计划事项。"));
            }
            if (item.getPlannedFinishDate() != null && (item.getPlannedFinishDate().isBefore(plan.getWeekStart())
                    || item.getPlannedFinishDate().isAfter(plan.getWeekEnd()))) {
                issues.add(blocking("WEEK_FINISH_DATE_INVALID", field + ".plannedFinishDate", "完成日期不在本周范围内",
                        "SYS_WEEK_05", String.valueOf(item.getPlannedFinishDate()), "将完成日期调整到当前自然周。"));
            }
        }
        return issues;
    }

    private List<Issue> dayRuleIssues(BizDayPlan plan, BizMonthPlanItem parent,
                                      WorkdayCalendarService.CalendarDay calendarDay) {
        List<Issue> issues = new ArrayList<>();
        if (!StringUtils.hasText(plan.getContent())) {
            issues.add(blocking("DAY_CONTENT_MISSING", "content", "日计划内容为空", "SYS_DAY_01",
                    "工作内容为空", "填写当天具体工作内容。"));
        }
        if (plan.getMonthPlanItemId() == null) {
            issues.add(issue("DAY_PARENT_MISSING", "RULE", "MEDIUM", "relatedMonthPlanItemId",
                    "日计划未关联月计划事项", "SYS_DAY_02", "未关联",
                    "系统无法核对该日计划是否支撑已审批的月计划事项。", "如属于计划内工作，请关联对应月计划事项。",
                    1D, List.of("日计划关联事项")));
        } else if (parent == null || Integer.valueOf(1).equals(parent.getDeleted())) {
            issues.add(blocking("DAY_PARENT_INVALID", "relatedMonthPlanItemId", "关联月计划事项不存在",
                    "SYS_DAY_03", String.valueOf(plan.getMonthPlanItemId()), "重新选择有效月计划事项。"));
        }
        if (!calendarDay.forceReport() && StringUtils.hasText(plan.getContent())) {
            issues.add(issue("NON_REQUIRED_DAY_PLAN", "RULE", "LOW", "planDate", "非强制填报日仍填写了日计划",
                    "SYS_DAY_04", String.valueOf(plan.getPlanDate()), "工作日规则显示该日期不强制填报，这不是违规，仅提示员工核对日期。",
                    "确认计划日期是否正确。", 1D, List.of("工作日规则:" + calendarDay.ruleType())));
        }
        return issues;
    }

    private List<Map<String, Object>> organizationStandards(Long deptId, String appliesTo) {
        if (deptId == null) {
            return List.of();
        }
        List<BizDeliverableTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<BizDeliverableTemplate>()
                .eq(BizDeliverableTemplate::getDeleted, 0)
                .eq(BizDeliverableTemplate::getDeptId, deptId)
                .eq(BizDeliverableTemplate::getStatus, "ENABLED")
                .like(BizDeliverableTemplate::getAppliesTo, appliesTo)
                .orderByAsc(BizDeliverableTemplate::getId));
        if (templates.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BizAcceptanceStandard>> standards = acceptanceStandardMapper.selectList(
                        new LambdaQueryWrapper<BizAcceptanceStandard>()
                                .eq(BizAcceptanceStandard::getDeleted, 0)
                                .eq(BizAcceptanceStandard::getStatus, "ENABLED")
                                .in(BizAcceptanceStandard::getTemplateId, templates.stream().map(BizDeliverableTemplate::getId).toList())
                                .orderByAsc(BizAcceptanceStandard::getTemplateId)
                                .orderByAsc(BizAcceptanceStandard::getId))
                .stream().collect(Collectors.groupingBy(BizAcceptanceStandard::getTemplateId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizDeliverableTemplate template : templates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", template.getId());
            item.put("templateName", template.getTemplateName());
            item.put("evidenceType", template.getEvidenceType());
            item.put("required", Boolean.TRUE.equals(template.getRequiredFlag()));
            item.put("description", defaultText(template.getDescription(), ""));
            item.put("bindingStrength", "REFERENCE_ONLY");
            item.put("note", "当前业务对象未显式绑定模板，只能作为部门参考标准，匹配不确定时不得判定违规。");
            item.put("acceptanceStandards", standards.getOrDefault(template.getId(), List.of()).stream().map(value -> Map.of(
                    "standard", value.getStandardText(),
                    "evidenceRequirement", defaultText(value.getEvidenceRequirement(), ""),
                    "requireReviewPassed", Boolean.TRUE.equals(value.getRequireReviewPassed())
            )).toList());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> planRules() {
        return List.of(
                rule("SEM_PLAN_01", "任务内容应具体说明行动、对象和预期结果，不能只有笼统表述。"),
                rule("SEM_PLAN_02", "交付物应明确可提交、可查看或可核验，并且与任务内容保持一致。"),
                rule("SEM_PLAN_03", "截止时间、任务范围和绩效权重应相互合理；依据不足时只能返回无法判断。"),
                rule("SEM_PLAN_04", "同一计划中的任务不应高度重复或相互冲突。")
        );
    }

    private List<Map<String, Object>> extraTaskRules() {
        return List.of(
                rule("SEM_PLAN_01", "额外任务内容应具体说明行动、对象和预期结果，不能只有笼统表述。"),
                rule("SEM_PLAN_02", "额外任务交付物应明确可提交、可查看或可核验，并且与任务内容保持一致。"),
                rule("SEM_EXTRA_01", "额外任务应当是原已审批月计划之外的新增工作；与原任务高度重合时应提示重复风险。"),
                rule("SEM_EXTRA_02", "新增任务的范围、期限和权重不应与原月计划形成明显不可执行冲突；依据不足时返回无法判断。")
        );
    }

    private List<Map<String, Object>> weekRules() {
        return List.of(
                rule("SEM_WEEK_01", "周计划内容应是父级月计划事项在本周的可执行拆解。"),
                rule("SEM_WEEK_02", "周交付物应体现本周阶段结果，并与父级月计划交付物方向一致。"),
                rule("SEM_WEEK_03", "一周工作范围和完成日期应具有可执行性；仅在内容或期限存在明确依据时判断，依据不足时返回无法判断。"),
                rule("SEM_WEEK_04", "当前周计划不应与同一父级事项的其他周计划机械重复。"),
                rule("SEM_WEEK_05", "周计划不得偏离父级月计划的任务内容和最终交付物。")
        );
    }

    private List<Map<String, Object>> dayRules() {
        return List.of(
                rule("SEM_DAY_01", "日计划应具体到当天可执行的行动与预期结果。"),
                rule("SEM_DAY_02", "日计划应支撑对应周计划或月计划事项，不得明显偏离。"),
                rule("SEM_DAY_03", "工作范围应适合在一天内推进；依据不足时返回无法判断。"),
                rule("SEM_DAY_04", "连续多日内容高度相同且没有阶段变化时，应提示复制或拆解不足风险。")
        );
    }

    private List<Map<String, Object>> resultRules() {
        return List.of(
                rule("SEM_RESULT_01", "证据内容必须与计划任务、任务内容和交付物相关。"),
                rule("SEM_RESULT_02", "每个交付物核验项必须分别判断证据覆盖状态，并引用页码、段落或Zip内文件路径。"),
                rule("SEM_RESULT_03", "成果说明与证据内容出现冲突时应明确指出双方原文。"),
                rule("SEM_RESULT_04", "只有上传文件但文件没有证明验收要求时，不能判定证据充分。"),
                rule("SEM_RESULT_05", "部门模板未与成果显式绑定时只能作为参考，不得仅凭模板直接判定违规。")
        );
    }

    private Map<String, Object> rule(String id, String text) {
        return Map.of("id", id, "title", semanticRuleTitle(id), "text", text);
    }

    private String semanticRuleTitle(String id) {
        return switch (id) {
            case "SEM_PLAN_01" -> "任务内容具体性";
            case "SEM_PLAN_02" -> "交付物可核验性";
            case "SEM_PLAN_03" -> "期限、范围与权重";
            case "SEM_PLAN_04" -> "任务重复与冲突";
            case "SEM_EXTRA_01" -> "额外任务新增性";
            case "SEM_EXTRA_02" -> "新增任务可执行性";
            case "SEM_WEEK_01" -> "月计划拆解合理性";
            case "SEM_WEEK_02" -> "阶段交付物一致性";
            case "SEM_WEEK_03" -> "本周范围可执行性";
            case "SEM_WEEK_04" -> "跨周重复风险";
            case "SEM_WEEK_05" -> "上下级计划一致性";
            case "SEM_DAY_01" -> "当日行动具体性";
            case "SEM_DAY_02" -> "上级计划支撑关系";
            case "SEM_DAY_03" -> "单日范围合理性";
            case "SEM_DAY_04" -> "连续重复风险";
            case "SEM_RESULT_01" -> "证据与计划相关性";
            case "SEM_RESULT_02" -> "验收项证据覆盖";
            case "SEM_RESULT_03" -> "成果说明一致性";
            case "SEM_RESULT_04" -> "证据内容充分性";
            case "SEM_RESULT_05" -> "部门标准适用性";
            default -> "语义检查";
        };
    }

    private Map<String, Object> acceptanceCriterion(String id, String text) {
        return Map.of("id", id, "text", text);
    }

    private ContextBundle bundle(String bizType, Long bizId, String version, Long ownerUserId, Long deptId,
                                 Map<String, Object> data, List<Map<String, Object>> rules,
                                 List<Issue> ruleIssues, Integer completionRate) {
        return new ContextBundle(bizType, bizId, version, ownerUserId, deptId, data, rules, ruleIssues,
                completionRate, hash(Map.of("bizType", bizType, "rules", rules, "businessData", data)));
    }

    private List<Issue> deduplicate(List<Issue> issues) {
        Map<String, Issue> result = new LinkedHashMap<>();
        for (Issue issue : issues) {
            String key = defaultText(issue.code(), "UNKNOWN") + "|"
                    + defaultText(issue.ruleId(), "UNKNOWN") + "|" + defaultText(issue.field(), "");
            Issue existing = result.get(key);
            if (existing == null || riskWeight(issue.severity()) > riskWeight(existing.severity())) {
                result.put(key, issue);
            }
        }
        return new ArrayList<>(result.values());
    }

    private String overallRisk(List<Issue> issues) {
        return issues.stream().map(Issue::severity).max(Comparator.comparingInt(this::riskWeight))
                .map(value -> "BLOCKING".equals(value) ? "HIGH" : value).orElse("LOW");
    }

    private int riskWeight(String severity) {
        return switch (defaultText(severity, "LOW").toUpperCase(Locale.ROOT)) {
            case "BLOCKING" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String summary(String status, String modelSummary, List<Issue> issues,
                           List<AnalysisDimension> dimensions, CompletionAssessment completion) {
        long blocking = issues.stream().filter(item -> "BLOCKING".equals(item.severity())).count();
        long high = issues.stream().filter(item -> "HIGH".equals(item.severity())).count();
        long medium = issues.stream().filter(item -> "MEDIUM".equals(item.severity())).count();
        long unknown = dimensions.stream().filter(item -> "UNKNOWN".equals(item.status())).count();
        String prefix = switch (status) {
            case "SUCCESS" -> defaultText(modelSummary, "千问语义检查完成。");
            case "MODEL_FAILED" -> "仅完成系统硬规则预检，千问调用失败；AI语义分析未执行，本结果不能用于判定计划或成果是否合理。";
            default -> "仅完成系统硬规则预检，千问尚未启用；AI语义分析未执行，本结果不能用于判定计划或成果是否合理。";
        };
        String counts = " 共发现" + blocking + "项阻断问题、" + high + "项高风险、" + medium + "项中风险。";
        if (unknown > 0) {
            counts += " 另有" + unknown + "项因依据不足无法判断。";
        }
        if (completion.suggestedMin() != null) {
            counts += " 依据验收项证据覆盖，建议完成比例为" + completion.suggestedMin() + "%～"
                    + completion.suggestedMax() + "%（仅供人工参考）。";
        }
        return prefix + counts;
    }

    private ReviewVO toVO(BizAiReview review, boolean stale) {
        try {
            ReviewResult result = objectMapper.readValue(review.getResultJson(), ReviewResult.class);
            return new ReviewVO(review.getId(), review.getBizType(), review.getBizId(), review.getBizVersion(),
                    review.getContentHash(), review.getReviewStatus(), review.getOverallRisk(), review.getProvider(),
                    review.getModelName(), review.getPromptVersion(), review.getCreatedAt(),
                    stale,
                    !"RULE_ONLY".equals(review.getReviewStatus()),
                    review.getErrorMessage(), result);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "AI检查结果解析失败");
        }
    }

    private boolean isStale(BizAiReview review) {
        if (review == null || !PLAN_TYPES.contains(review.getBizType())) {
            return false;
        }
        try {
            ContextBundle current = buildPlanContext(review.getBizType(), review.getBizId());
            return !Objects.equals(review.getOwnerUserId(), current.ownerUserId())
                    || !Objects.equals(review.getContentHash(), current.contentHash());
        } catch (BizException ex) {
            if (ex.getCode() == 404 || ex.getCode() == 422) {
                return true;
            }
            throw ex;
        }
    }

    private void requireViewer(AuthUser user, Long ownerUserId) {
        if (Objects.equals(user.userId(), ownerUserId) || hasRole(user, "SUPER_ADMIN")) {
            return;
        }
        if (Objects.equals(dataScopeService.directLeaderId(ownerUserId), user.userId())) {
            return;
        }
        boolean departmentRole = hasRole(user, "DEPT_OWNER") || hasRole(user, "DEPT_LEADER");
        if (departmentRole && dataScopeService.departmentOwnerIds(user, null).contains(ownerUserId)) {
            return;
        }
        boolean scopedLeaderRole = hasRole(user, "PROJECT_MANAGER") || hasRole(user, "DIRECT_LEADER");
        if (scopedLeaderRole && dataScopeService.leaderOwnerIds(user, null).contains(ownerUserId)) {
            return;
        }
        throw new BizException(403, "无权查看该AI检查结果");
    }

    private void requireOwner(AuthUser user, Long ownerUserId) {
        if (!Objects.equals(user.userId(), ownerUserId)) {
            throw new BizException(403, "只能检查本人填写的计划或成果");
        }
    }

    private BizAiReview latestByHash(Long ownerUserId, String bizType, String contentHash,
                                     boolean modelEnabledForUser) {
        String provider = qwenAiClient.provider();
        String model = qwenAiClient.model();
        String promptVersion = qwenAiClient.promptVersion();
        String reusableStatus = modelEnabledForUser ? "SUCCESS" : "RULE_ONLY";
        BizAiReview completed = reviewMapper.selectOne(new LambdaQueryWrapper<BizAiReview>()
                .eq(BizAiReview::getDeleted, 0)
                .eq(BizAiReview::getOwnerUserId, ownerUserId)
                .eq(BizAiReview::getBizType, bizType)
                .eq(BizAiReview::getContentHash, contentHash)
                .eq(BizAiReview::getProvider, provider)
                .eq(BizAiReview::getModelName, model)
                .eq(BizAiReview::getPromptVersion, promptVersion)
                .eq(BizAiReview::getReviewStatus, reusableStatus)
                .orderByDesc(BizAiReview::getCreatedAt)
                .orderByDesc(BizAiReview::getId)
                .last("LIMIT 1"));
        if (completed != null || !modelEnabledForUser) {
            return completed;
        }
        return reviewMapper.selectOne(new LambdaQueryWrapper<BizAiReview>()
                .eq(BizAiReview::getDeleted, 0)
                .eq(BizAiReview::getOwnerUserId, ownerUserId)
                .eq(BizAiReview::getBizType, bizType)
                .eq(BizAiReview::getContentHash, contentHash)
                .eq(BizAiReview::getProvider, provider)
                .eq(BizAiReview::getModelName, model)
                .eq(BizAiReview::getPromptVersion, promptVersion)
                .eq(BizAiReview::getReviewStatus, "MODEL_FAILED")
                .ge(BizAiReview::getCreatedAt, LocalDateTime.now().minusSeconds(RECENT_FAILURE_REUSE_SECONDS))
                .orderByDesc(BizAiReview::getCreatedAt)
                .orderByDesc(BizAiReview::getId)
                .last("LIMIT 1"));
    }

    private boolean reusableWithCurrentConfiguration(BizAiReview review, boolean modelEnabledForUser) {
        boolean sameConfiguration = Objects.equals(review.getProvider(), qwenAiClient.provider())
                && Objects.equals(review.getModelName(), qwenAiClient.model())
                && Objects.equals(review.getPromptVersion(), qwenAiClient.promptVersion());
        if (!sameConfiguration) return false;
        if (Objects.equals(review.getReviewStatus(), modelEnabledForUser ? "SUCCESS" : "RULE_ONLY")) {
            return true;
        }
        return modelEnabledForUser
                && "MODEL_FAILED".equals(review.getReviewStatus())
                && review.getCreatedAt() != null
                && review.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RECENT_FAILURE_REUSE_SECONDS));
    }

    private BizMonthPlan requireMonthPlan(Long id) {
        BizMonthPlan plan = monthPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划不存在");
        }
        return plan;
    }

    private BizMonthPlanItem requireMonthItem(Long id) {
        BizMonthPlanItem item = monthPlanItemMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "月计划事项不存在");
        }
        return item;
    }

    private List<BizMonthPlanItem> monthItems(Long monthPlanId) {
        return monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, monthPlanId)
                .orderByAsc(BizMonthPlanItem::getSortNo)
                .orderByAsc(BizMonthPlanItem::getId));
    }

    private BizWeekPlanItem inferWeekItem(BizDayPlan dayPlan) {
        if (dayPlan.getMonthPlanItemId() == null || dayPlan.getPlanDate() == null) {
            return null;
        }
        LocalDate monday = dayPlan.getPlanDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BizWeekPlan weekPlan = weekPlanMapper.selectOne(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .eq(BizWeekPlan::getOwnerUserId, dayPlan.getOwnerUserId())
                .eq(BizWeekPlan::getWeekStart, monday)
                .orderByDesc(BizWeekPlan::getId)
                .last("LIMIT 1"));
        if (weekPlan == null) {
            return null;
        }
        return weekPlanItemMapper.selectOne(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getDeleted, 0)
                .eq(BizWeekPlanItem::getWeekPlanId, weekPlan.getId())
                .eq(BizWeekPlanItem::getMonthPlanItemId, dayPlan.getMonthPlanItemId())
                .last("LIMIT 1"));
    }

    private List<Map<String, Object>> relatedWeekContexts(BizWeekPlan current, List<BizWeekPlanItem> items) {
        Set<Long> parentIds = items.stream().map(BizWeekPlanItem::getMonthPlanItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return List.of();
        }
        List<BizWeekPlanItem> relatedItems = weekPlanItemMapper.selectList(new LambdaQueryWrapper<BizWeekPlanItem>()
                .eq(BizWeekPlanItem::getDeleted, 0)
                .ne(BizWeekPlanItem::getWeekPlanId, current.getId())
                .in(BizWeekPlanItem::getMonthPlanItemId, parentIds)
                .orderByAsc(BizWeekPlanItem::getWeekPlanId)
                .orderByAsc(BizWeekPlanItem::getSortNo));
        if (relatedItems.isEmpty()) {
            return List.of();
        }
        Map<Long, List<BizWeekPlanItem>> itemsByWeek = relatedItems.stream()
                .collect(Collectors.groupingBy(BizWeekPlanItem::getWeekPlanId, LinkedHashMap::new, Collectors.toList()));
        List<BizWeekPlan> relatedPlans = weekPlanMapper.selectList(new LambdaQueryWrapper<BizWeekPlan>()
                .eq(BizWeekPlan::getDeleted, 0)
                .in(BizWeekPlan::getId, itemsByWeek.keySet())
                .orderByDesc(BizWeekPlan::getWeekStart));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizWeekPlan relatedPlan : relatedPlans) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekPlan", mapWeekPlan(relatedPlan));
            row.put("items", itemsByWeek.getOrDefault(relatedPlan.getId(), List.of()).stream()
                    .map(this::mapWeekItem).toList());
            result.add(row);
        }
        return result;
    }

    private List<BizDayPlan> recentDayPlans(BizDayPlan current) {
        return dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, current.getOwnerUserId())
                .ne(current.getId() != null, BizDayPlan::getId, current.getId())
                .between(BizDayPlan::getPlanDate, current.getPlanDate().minusDays(7), current.getPlanDate().minusDays(1))
                .orderByDesc(BizDayPlan::getPlanDate)
                .last("LIMIT 7"));
    }

    private long duplicateEvidenceCount(Long ownerUserId, String checksum) {
        List<BizResultEvidence> evidences = resultEvidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                .eq(BizResultEvidence::getDeleted, 0)
                .eq(BizResultEvidence::getChecksum, checksum));
        if (evidences.isEmpty()) {
            return 0L;
        }
        return resultMapper.selectCount(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .in(BizResult::getId, evidences.stream().map(BizResultEvidence::getResultId).toList()));
    }

    private Map<String, Object> mapMonthPlan(BizMonthPlan value) {
        if (value == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.getId());
        map.put("title", defaultText(value.getTitle(), ""));
        map.put("planMonth", defaultText(value.getPlanMonth(), ""));
        map.put("summary", defaultText(value.getContent(), ""));
        map.put("status", defaultText(value.getStatus(), ""));
        return map;
    }

    private Map<String, Object> mapMonthItem(BizMonthPlanItem value) {
        if (value == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.getId());
        map.put("taskType", defaultText(value.getTaskType(), "REGULAR"));
        map.put("taskName", defaultText(value.getTaskName(), ""));
        map.put("taskContent", defaultText(value.getTaskContent(), ""));
        map.put("deliverable", defaultText(value.getDeliverable(), ""));
        map.put("deadline", value.getDeadline());
        map.put("performanceWeight", value.getPerformanceWeight());
        return map;
    }

    private Map<String, Object> mapMonthItemContent(BizMonthPlanItem value) {
        Map<String, Object> map = new LinkedHashMap<>(mapMonthItem(value));
        map.remove("id");
        return map;
    }

    private Map<String, Object> mapWeekPlan(BizWeekPlan value) {
        if (value == null) return Map.of();
        return Map.of("id", value.getId(), "weekStart", value.getWeekStart(), "weekEnd", value.getWeekEnd(),
                "status", defaultText(value.getStatus(), ""));
    }

    private Map<String, Object> mapWeekItem(BizWeekPlanItem value) {
        if (value == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.getId());
        map.put("monthPlanItemId", value.getMonthPlanItemId());
        map.put("content", defaultText(value.getContent(), ""));
        map.put("deliverable", defaultText(value.getDeliverable(), "（未填写）"));
        map.put("plannedFinishDate", value.getPlannedFinishDate());
        return map;
    }

    private Map<String, Object> mapDayPlan(BizDayPlan value) {
        if (value == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.getId());
        map.put("planDate", value.getPlanDate());
        map.put("content", defaultText(value.getContent(), ""));
        map.put("remark", defaultText(value.getRemark(), ""));
        map.put("relatedMonthPlanItemId", value.getMonthPlanItemId());
        return map;
    }

    private Map<String, Object> mapCalendar(WorkdayCalendarService.CalendarDay value) {
        return Map.of("date", value.date(), "ruleType", value.ruleType(), "forceReport", value.forceReport(),
                "description", defaultText(value.description(), ""), "explicitRule", value.explicit());
    }

    private Issue blocking(String code, String field, String title, String ruleId, String quote, String suggestion) {
        return issue(code, "RULE", "BLOCKING", field, title, ruleId, quote,
                "该问题违反系统明确的提交规则。", suggestion, 1D, List.of(field));
    }

    private Issue issue(String code, String source, String severity, String field, String title, String ruleId,
                        String quote, String basis, String suggestion, Double confidence, List<String> references) {
        return new Issue(code, source, severity, field, title, ruleId, quote, basis, suggestion, confidence, references);
    }

    private String normalizePlanType(String value) {
        String normalized = normalizeAnyType(value);
        if (!PLAN_TYPES.contains(normalized)) {
            throw new BizException(422, "仅支持月计划、周计划、日计划和额外任务AI检查");
        }
        return normalized;
    }

    private String normalizeAnyType(String value) {
        String normalized = defaultText(value, "").trim().toUpperCase(Locale.ROOT);
        if (!PLAN_TYPES.contains(normalized) && !RESULT.equals(normalized)) {
            throw new BizException(422, "不支持的AI检查对象类型");
        }
        return normalized;
    }

    private boolean hasRole(AuthUser user, String role) {
        return user.roles() != null && user.roles().contains(role);
    }

    private String hash(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] canonicalJson = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(value);
            byte[] bytes = digest.digest(canonicalJson);
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new BizException(500, "生成AI检查内容摘要失败");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "保存AI检查结果失败");
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String versionOf(LocalDateTime updatedAt, LocalDateTime submitAt) {
        LocalDateTime value = updatedAt == null ? submitAt : updatedAt;
        return value == null ? "1" : value.toString();
    }

    private record ContextBundle(
            String bizType,
            Long bizId,
            String bizVersion,
            Long ownerUserId,
            Long deptId,
            Map<String, Object> businessData,
            List<Map<String, Object>> rules,
            List<Issue> ruleIssues,
            Integer declaredCompletionRate,
            String contentHash
    ) {
    }

}
