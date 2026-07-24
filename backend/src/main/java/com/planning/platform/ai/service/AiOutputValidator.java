package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.model.PlanDraftAiModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiOutputValidator {

    private static final Set<String> WORK_TYPES = Set.of(
            "TASK", "METRIC", "DOCUMENT", "PROJECT", "COMMUNICATION", "ROUTINE", "UNKNOWN");
    private static final Set<String> LEVELS = Set.of("INFO", "WARNING", "HIGH");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper objectMapper;

    public AiModels.GenerateResponse validateGenerate(String content, String suggestionId, String planMonth,
                                                       List<String> missingContext) {
        JsonNode root = parse(content);
        String summary = requiredText(root, "summary", 5000);
        JsonNode rawItems = root.path("items");
        if (!rawItems.isArray() || rawItems.isEmpty() || rawItems.size() > 20) {
            throw new OutputException("items 必须包含 1 至 20 条任务");
        }
        List<AiModels.PlanItem> items = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (JsonNode rawItem : rawItems) {
            AiModels.PlanItem item = planItem(rawItem, planMonth);
            items.add(item);
            totalWeight = totalWeight.add(item.performanceWeight());
        }
        List<String> warnings = stringList(root.path("warnings"), 20, 500);
        if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
            warnings = new ArrayList<>(warnings);
            warnings.add("常规任务绩效权重合计为 " + totalWeight.stripTrailingZeros().toPlainString() + "% ，应用后请调整为 100%");
        }
        return new AiModels.GenerateResponse(suggestionId, summary, items, warnings,
                missingContext == null ? List.of() : List.copyOf(missingContext), AiModels.NOTICE);
    }

    public AiModels.OptimizeResponse validateOptimize(String content, String suggestionId, String planMonth) {
        JsonNode root = parse(content);
        JsonNode rawItem = root.path("item");
        if (!rawItem.isObject()) throw new OutputException("item 必须为对象");
        return new AiModels.OptimizeResponse(suggestionId, planItem(rawItem, planMonth),
                stringList(root.path("warnings"), 20, 500), AiModels.NOTICE);
    }

    public AiModels.CheckResponse validateCheck(String content, String suggestionId) {
        JsonNode root = parse(content);
        JsonNode rawIssues = root.path("issues");
        if (!rawIssues.isArray() || rawIssues.size() > 50) {
            throw new OutputException("issues 必须为不超过 50 条的数组");
        }
        List<AiModels.CheckIssue> issues = new ArrayList<>();
        for (JsonNode raw : rawIssues) {
            String level = requiredText(raw, "level", 20).toUpperCase();
            if (!LEVELS.contains(level)) throw new OutputException("检查等级无效");
            String path = requiredText(raw, "fieldPath", 200);
            if (!path.matches("(summary|items(?:\\[\\d+])?(?:\\.[A-Za-z]+)?)")) {
                throw new OutputException("检查字段路径无效");
            }
            issues.add(new AiModels.CheckIssue(requiredText(raw, "code", 60), level, path,
                    requiredText(raw, "message", 500), requiredText(raw, "suggestion", 1000)));
        }
        return new AiModels.CheckResponse(suggestionId, issues, "检查结果仅供参考，不代替系统业务校验");
    }

    public PlanDraftAiModels.WeekDraft validateWeekDraft(String content, String suggestionId,
                                                          LocalDate weekStart, Set<Long> allowedMonthItemIds,
                                                          List<String> missingContext) {
        JsonNode root = parse(content);
        rejectUnknownFields(root, Set.of("items", "warnings"));
        JsonNode rawItems = root.path("items");
        if (!rawItems.isArray() || rawItems.isEmpty() || rawItems.size() > 100) {
            throw new OutputException("items 必须包含 1 至 100 条周任务");
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        List<PlanDraftAiModels.WeekItem> items = new ArrayList<>();
        Set<Long> usedParents = new HashSet<>();
        for (JsonNode raw : rawItems) {
            rejectUnknownFields(raw, Set.of("monthPlanItemId", "content", "deliverable", "plannedFinishDate"));
            Long parentId = requiredLong(raw, "monthPlanItemId");
            if (allowedMonthItemIds == null || !allowedMonthItemIds.contains(parentId)) {
                throw new OutputException("关联月计划事项不在当前员工可用范围内");
            }
            if (!usedParents.add(parentId)) throw new OutputException("同一月计划事项不能重复关联");
            LocalDate finishDate = optionalDate(raw, "plannedFinishDate");
            if (finishDate != null && (finishDate.isBefore(weekStart) || finishDate.isAfter(weekEnd))) {
                throw new OutputException("周任务完成日期必须位于所选自然周内");
            }
            String workContent = requiredText(raw, "content", 5000);
            if (!workContent.matches(".*\\p{L}.*")) {
                throw new OutputException("content 必须是包含文字的可执行工作描述");
            }
            items.add(new PlanDraftAiModels.WeekItem(parentId, workContent,
                    requiredText(raw, "deliverable", 500), finishDate));
        }
        return new PlanDraftAiModels.WeekDraft(suggestionId, List.copyOf(items),
                stringList(root.path("warnings"), 20, 500), copy(missingContext), AiModels.NOTICE);
    }

    public PlanDraftAiModels.DayDraft validateDayDraft(String content, String suggestionId,
                                                        Set<Long> allowedMonthItemIds,
                                                        List<String> missingContext) {
        JsonNode root = parse(content);
        rejectUnknownFields(root, Set.of("relatedMonthPlanItemId", "content", "remark", "warnings"));
        Long parentId = root.path("relatedMonthPlanItemId").isNull()
                || root.path("relatedMonthPlanItemId").isMissingNode()
                ? null : requiredLong(root, "relatedMonthPlanItemId");
        if (parentId != null && (allowedMonthItemIds == null || !allowedMonthItemIds.contains(parentId))) {
            throw new OutputException("关联月计划事项不在当前员工可用范围内");
        }
        return new PlanDraftAiModels.DayDraft(suggestionId, parentId,
                requiredText(root, "content", 5000), text(root, "remark", 500, ""),
                stringList(root.path("warnings"), 20, 500), copy(missingContext), AiModels.NOTICE);
    }

    private AiModels.PlanItem planItem(JsonNode node, String planMonth) {
        String workType = text(node, "workType", 30, "UNKNOWN").toUpperCase();
        if (!WORK_TYPES.contains(workType)) workType = "UNKNOWN";
        BigDecimal weight = decimal(node, "performanceWeight");
        if (weight.compareTo(new BigDecimal("0.01")) < 0 || weight.compareTo(new BigDecimal("100")) > 0) {
            throw new OutputException("绩效权重必须在 0.01 至 100 之间");
        }
        LocalDate deadline;
        try {
            deadline = LocalDate.parse(requiredText(node, "deadline", 10));
        } catch (DateTimeException ex) {
            throw new OutputException("截止日期格式必须为 yyyy-MM-dd");
        }
        if (!YearMonth.from(deadline).equals(YearMonth.parse(planMonth)) || deadline.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new OutputException("截止日期必须在计划月份内且不能早于今天");
        }
        return new AiModels.PlanItem(workType, requiredText(node, "taskName", 120),
                requiredText(node, "taskContent", 5000), requiredText(node, "deliverable", 500),
                deadline, weight);
    }

    private JsonNode parse(String content) {
        if (!StringUtils.hasText(content)) throw new OutputException("模型输出为空");
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            if (!root.isObject()) throw new OutputException("模型输出根节点必须为对象");
            return root;
        } catch (OutputException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OutputException("模型输出不是合法 JSON");
        }
    }

    private String requiredText(JsonNode node, String field, int max) {
        String value = text(node, field, max, "");
        if (!StringUtils.hasText(value)) throw new OutputException(field + " 不能为空");
        return value;
    }

    private String text(JsonNode node, String field, int max, String defaultValue) {
        String value = node.path(field).asText(defaultValue).trim();
        if (value.length() > max) throw new OutputException(field + " 超过长度限制 " + max);
        return value;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        try {
            if (!node.has(field) || !node.get(field).isNumber()) throw new NumberFormatException();
            return node.get(field).decimalValue().stripTrailingZeros();
        } catch (Exception ex) {
            throw new OutputException(field + " 必须为数字");
        }
    }

    private Long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.longValue() <= 0) {
            throw new OutputException(field + " 必须为正整数");
        }
        return value.longValue();
    }

    private LocalDate optionalDate(JsonNode node, String field) {
        String value = text(node, field, 10, "");
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException ex) {
            throw new OutputException(field + " 格式必须为 yyyy-MM-dd");
        }
    }

    private void rejectUnknownFields(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new OutputException("AI 输出包含未授权字段: " + field);
        });
    }

    private List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private List<String> stringList(JsonNode node, int maxItems, int maxLength) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > maxItems) throw new OutputException("提示列表格式无效");
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (value.length() > maxLength) throw new OutputException("提示内容超过长度限制");
            if (StringUtils.hasText(value)) result.add(value);
        }
        return result;
    }

    public static class OutputException extends RuntimeException {
        public OutputException(String message) {
            super(message);
        }
    }
}
