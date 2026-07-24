package com.planning.platform.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.model.AiModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MockAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String providerCode) {
        return "MOCK".equalsIgnoreCase(providerCode);
    }

    @Override
    public ProviderResponse complete(ProviderConfig config, ProviderRequest request) {
        ObjectNode output = switch (request.sceneCode()) {
            case AiModels.MONTH_PLAN_DRAFT -> draft(request.context());
            case AiModels.MONTH_PLAN_ITEM_OPTIMIZE -> optimize(request.context());
            case AiModels.MONTH_PLAN_CHECK -> check(request.context());
            case AiModels.WEEK_PLAN_DRAFT, AiModels.WEEK_PLAN_ADJUST -> weekDraft(request.context());
            case AiModels.DAY_PLAN_DRAFT, AiModels.DAY_PLAN_ADJUST -> dayDraft(request.context());
            default -> objectMapper.createObjectNode().put("status", "ok");
        };
        try {
            String content = objectMapper.writeValueAsString(output);
            int input = Math.max(1, request.context().toString().length() / 4);
            return new ProviderResponse(content, input, Math.max(1, content.length() / 4));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ObjectNode weekDraft(JsonNode context) {
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode items = output.putArray("items");
        JsonNode parents = context.path("parentOptions");
        LocalDate start = LocalDate.parse(context.path("weekStart").asText(LocalDate.now().toString()));
        if (parents.isArray()) {
            for (int i = 0; i < Math.min(parents.size(), 6); i++) {
                JsonNode parent = parents.get(i);
                String name = parent.path("taskName").asText("本周重点任务");
                ObjectNode item = items.addObject();
                item.put("monthPlanItemId", parent.path("id").asLong());
                item.put("content", shorten("推进并完成“" + name + "”的本周工作", 5000));
                item.put("deliverable", shorten(parent.path("deliverable").asText(name + "成果"), 500));
                item.put("plannedFinishDate", start.plusDays(Math.min(4, i)).toString());
            }
        }
        output.putArray("warnings");
        return output;
    }

    private ObjectNode dayDraft(JsonNode context) {
        JsonNode parents = context.path("parentOptions");
        JsonNode parent = parents.isArray() && !parents.isEmpty() ? parents.get(0) : null;
        String intent = context.path("intentText").asText("推进今日重点工作");
        ObjectNode output = objectMapper.createObjectNode();
        if (parent == null) output.putNull("relatedMonthPlanItemId");
        else output.put("relatedMonthPlanItemId", parent.path("id").asLong());
        output.put("content", shorten(intent, 5000));
        output.put("remark", "按计划完成并及时同步风险");
        output.putArray("warnings");
        return output;
    }

    private ObjectNode draft(JsonNode context) {
        String intent = context.path("intentText").asText("完成本月重点工作").trim();
        String monthText = context.path("planMonth").asText(YearMonth.now().toString());
        YearMonth month = YearMonth.parse(monthText);
        List<String> tasks = new ArrayList<>(Arrays.stream(intent.split("[\\r\\n，。；;、]+"))
                .map(String::trim).filter(StringUtils::hasText).limit(6).toList());
        if (tasks.isEmpty()) tasks.add("完成本月重点工作");
        ObjectNode output = objectMapper.createObjectNode();
        output.put("summary", "本月围绕" + shorten(intent, 90) + "推进重点工作并形成可验收成果");
        ArrayNode items = output.putArray("items");
        BigDecimal baseWeight = BigDecimal.valueOf(100)
                .divide(BigDecimal.valueOf(tasks.size()), 2, RoundingMode.DOWN);
        BigDecimal assigned = BigDecimal.ZERO;
        LocalDate start = month.atDay(1);
        if (month.equals(YearMonth.now()) && LocalDate.now().isAfter(start)) start = LocalDate.now();
        int availableDays = Math.max(1, (int) (month.atEndOfMonth().toEpochDay() - start.toEpochDay() + 1));
        for (int index = 0; index < tasks.size(); index++) {
            String task = tasks.get(index);
            BigDecimal weight = index == tasks.size() - 1
                    ? BigDecimal.valueOf(100).subtract(assigned) : baseWeight;
            assigned = assigned.add(weight);
            int offset = Math.min(availableDays - 1, Math.max(0, ((index + 1) * availableDays / tasks.size()) - 1));
            ObjectNode item = items.addObject();
            item.put("workType", inferWorkType(task));
            item.put("taskName", shorten(task, 120));
            item.put("taskContent", shorten("推进并完成“" + task + "”，明确过程节点并闭环相关事项", 5000));
            item.put("deliverable", shorten(task + "成果材料", 500));
            item.put("deadline", start.plusDays(offset).toString());
            item.put("performanceWeight", weight);
        }
        output.putArray("warnings");
        return output;
    }

    private ObjectNode optimize(JsonNode context) {
        JsonNode source = context.path("item");
        String instruction = context.path("instruction").asText("");
        String taskName = text(source, "taskName", instruction, "本月重点任务");
        String monthText = context.path("planMonth").asText(YearMonth.now().toString());
        YearMonth month = YearMonth.parse(monthText);
        LocalDate deadline = parseDeadline(source.path("deadline").asText(), month);
        ObjectNode item = objectMapper.createObjectNode();
        item.put("workType", text(source, "workType", inferWorkType(taskName)));
        item.put("taskName", shorten(taskName, 120));
        item.put("taskContent", shorten(text(source, "taskContent", "推进并完成“" + taskName + "”，明确责任、节点和交付要求"), 5000));
        item.put("deliverable", shorten(text(source, "deliverable", taskName + "成果材料"), 500));
        item.put("deadline", deadline.toString());
        double sourceWeight = source.path("performanceWeight").asDouble(10);
        item.put("performanceWeight", sourceWeight > 0 ? sourceWeight : 10);
        ObjectNode output = objectMapper.createObjectNode();
        output.set("item", item);
        output.putArray("warnings");
        return output;
    }

    private ObjectNode check(JsonNode context) {
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode issues = output.putArray("issues");
        JsonNode items = context.path("currentForm").path("items");
        if (!items.isArray() || items.isEmpty()) {
            addIssue(issues, "PLAN_ITEMS_EMPTY", "HIGH", "items", "月计划尚未包含任务", "至少补充一条可执行、可验收的任务");
            return output;
        }
        double totalWeight = 0;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String base = "items[" + i + "]";
            totalWeight += item.path("performanceWeight").asDouble(0);
            if (!StringUtils.hasText(item.path("deliverable").asText())) {
                addIssue(issues, "DELIVERABLE_MISSING", "HIGH", base + ".deliverable", "交付物为空", "填写可查看或可下载的成果名称");
            }
        }
        if (Math.abs(totalWeight - 100) > 0.001) {
            addIssue(issues, "WEIGHT_TOTAL_INVALID", "HIGH", "items", "绩效权重合计为 " + totalWeight + "%", "调整常规任务权重合计为 100% ");
        }
        return output;
    }

    private void addIssue(ArrayNode issues, String code, String level, String path, String message, String suggestion) {
        ObjectNode issue = issues.addObject();
        issue.put("code", code);
        issue.put("level", level);
        issue.put("fieldPath", path);
        issue.put("message", message);
        issue.put("suggestion", suggestion);
    }

    private String inferWorkType(String text) {
        if (text.matches(".*(报告|文档|方案|制度|模板).*")) return "DOCUMENT";
        if (text.matches(".*(项目|上线|版本|建设|交付).*")) return "PROJECT";
        if (text.matches(".*(指标|增长|完成率|金额|数量).*")) return "METRIC";
        if (text.matches(".*(沟通|协调|会议|访谈).*")) return "COMMUNICATION";
        return "TASK";
    }

    private LocalDate parseDeadline(String value, YearMonth month) {
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (YearMonth.from(parsed).equals(month) && !parsed.isBefore(LocalDate.now())) return parsed;
        } catch (Exception ignored) {
        }
        LocalDate result = month.atEndOfMonth();
        return result.isBefore(LocalDate.now()) ? LocalDate.now() : result;
    }

    private String text(JsonNode node, String field, String... fallbacks) {
        String value = node.path(field).asText("").trim();
        if (StringUtils.hasText(value)) return value;
        return Arrays.stream(fallbacks).filter(StringUtils::hasText).findFirst().orElse("");
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
