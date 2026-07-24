package com.planning.platform.ai.service;

import com.planning.platform.ai.model.AiReviewModels.AcceptanceCoverage;
import com.planning.platform.ai.model.AiReviewModels.AnalysisDimension;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.Issue;
import com.planning.platform.ai.model.AiReviewModels.ModelAnalysis;
import com.planning.platform.ai.model.AiReviewModels.SourceReference;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.text.Normalizer;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a closed source catalog for the model and rejects conclusions that cannot be traced back
 * to the exact request. The model may explain a conclusion, but it cannot introduce new rules,
 * acceptance criteria or evidence locations.
 */
@Service
public class AiGroundingService {

    private static final Pattern EVIDENCE_MARKER = Pattern.compile("(?m)^(\\[证据:[^\\r\\n]+\\])\\s*$");
    private final AiRedactionService redactionService;

    @Autowired
    public AiGroundingService(AiRedactionService redactionService) {
        this.redactionService = redactionService;
    }

    AiGroundingService() {
        this(new AiRedactionService());
    }

    public AnalysisRequest prepare(String bizType, List<Map<String, Object>> rules,
                                   Map<String, Object> businessData) {
        List<SourceReference> sources = new ArrayList<>();
        collectSources(businessData, "businessData", sources);
        Map<String, List<String>> requiredReferences = new LinkedHashMap<>();
        for (String ruleId : allowedRuleIds(rules)) {
            List<String> sourceIds = sources.stream()
                    .filter(source -> requiredForRule(ruleId, source.path()))
                    .map(SourceReference::id)
                    .toList();
            if (!sourceIds.isEmpty()) {
                requiredReferences.put(ruleId, sourceIds);
            }
        }
        return new AnalysisRequest(bizType, rules, businessData, sources, requiredReferences);
    }

    public ModelAnalysis validate(AnalysisRequest request, ModelAnalysis raw) {
        if (raw == null) {
            return emptyAnalysis();
        }
        Set<String> allowedRuleIds = allowedRuleIds(request.rules());
        Map<String, SourceReference> sources = sourcesById(request.sourceCatalog());
        List<Issue> issues = raw.issues() == null ? List.of() : raw.issues().stream()
                .map(issue -> validateIssue(issue, allowedRuleIds, sources))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<AcceptanceCoverage> coverage = validateCoverage(request.businessData(), raw.acceptanceCoverage(), sources);
        List<AnalysisDimension> dimensions = validateDimensions(
                request, raw.analysisDimensions(), issues, coverage, sources);
        return new ModelAnalysis("千问已按检查规则逐项分析，以下结论均已通过来源原文校验。",
                issues, dimensions, coverage);
    }

    private List<AnalysisDimension> validateDimensions(AnalysisRequest request,
                                                        List<AnalysisDimension> rawDimensions,
                                                        List<Issue> validatedIssues,
                                                        List<AcceptanceCoverage> validatedCoverage,
                                                        Map<String, SourceReference> sources) {
        Map<String, String> rules = allowedRules(request.rules());
        Map<String, AnalysisDimension> rawByRule = new LinkedHashMap<>();
        if (rawDimensions != null) {
            for (AnalysisDimension item : rawDimensions) {
                if (item != null && rules.containsKey(trim(item.ruleId()))) {
                    rawByRule.putIfAbsent(trim(item.ruleId()), item);
                }
            }
        }
        Set<String> issueRuleIds = validatedIssues.stream().map(Issue::ruleId).collect(java.util.stream.Collectors.toSet());
        List<AnalysisDimension> result = new ArrayList<>();
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if ("SEM_RESULT_02".equals(rule.getKey()) && hasUnprovenCoverage(validatedCoverage)) {
                result.add(coverageRiskDimension(rule.getKey(), rule.getValue(), validatedCoverage));
                continue;
            }
            AnalysisDimension raw = rawByRule.get(rule.getKey());
            if (raw == null || !StringUtils.hasText(raw.basis())) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(), "模型未返回该检查维度的可核验结论。"));
                continue;
            }
            String status = normalizeDimensionStatus(raw.status());
            if ("RISK".equals(status) && !issueRuleIds.contains(rule.getKey())) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(), "模型标记了风险，但对应问题未通过规则编号或原文引用校验。"));
                continue;
            }
            if ("UNKNOWN".equals(status)) {
                result.add(new AnalysisDimension(rule.getKey(), defaultText(raw.title(), rule.getValue()), status,
                        defaultText(raw.conclusion(), raw.basis()), "", trim(raw.basis()),
                        confidence(raw.confidence()), List.of()));
                continue;
            }
            List<SourceReference> cited = resolveAll(raw.references(), sources);
            if (cited.stream().anyMatch(source -> !relevantForRule(rule.getKey(), source.path()))) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(),
                        "模型引用了与本检查维度无关的字段，不能形成可靠结论。"));
                continue;
            }
            String groundedQuote = verifiedQuote(raw.quote(), cited, "PASS".equals(status));
            if (cited.isEmpty() || !StringUtils.hasText(groundedQuote)) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(), "模型给出的检查依据未通过原文真实性校验。"));
                continue;
            }
            if ("PASS".equals(status) && !coversRequiredSources(rule.getKey(), request, cited)) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(),
                        "模型只核对了部分计划内容，未覆盖本维度所需的全部关键字段，不能判定为通过。"));
                continue;
            }
            if ("PASS".equals(status) && conflictsWithSubstantiveRisk(rule.getKey(), validatedIssues)) {
                result.add(unknownDimension(rule.getKey(), rule.getValue(),
                        "本维度依赖的关键字段已在其他检查项中发现实质风险，不能同时判定为通过。"));
                continue;
            }
            result.add(new AnalysisDimension(rule.getKey(), rule.getValue(), status,
                    defaultText(raw.conclusion(), raw.basis()), groundedQuote, trim(raw.basis()),
                    confidence(raw.confidence()), cited.stream().map(this::safeLabel).distinct().toList()));
        }
        return result;
    }

    private boolean hasUnprovenCoverage(List<AcceptanceCoverage> coverage) {
        return coverage != null && coverage.stream().anyMatch(item -> item != null
                && Set.of("PARTIAL", "UNPROVEN").contains(normalizeCoverage(item.status())));
    }

    private AnalysisDimension coverageRiskDimension(String ruleId, String title,
                                                     List<AcceptanceCoverage> coverage) {
        List<AcceptanceCoverage> affected = coverage.stream()
                .filter(item -> item != null
                        && Set.of("PARTIAL", "UNPROVEN").contains(normalizeCoverage(item.status())))
                .toList();
        String quote = affected.stream().map(AcceptanceCoverage::evidenceQuote)
                .filter(StringUtils::hasText).findFirst().orElse("");
        List<String> references = affected.stream()
                .filter(item -> item.evidenceReferences() != null)
                .flatMap(item -> item.evidenceReferences().stream())
                .filter(StringUtils::hasText).distinct().toList();
        long unproven = affected.stream().filter(item -> "UNPROVEN".equals(normalizeCoverage(item.status()))).count();
        long partial = affected.size() - unproven;
        String basis = "逐项证据校验发现" + unproven + "项未证明、" + partial + "项部分证明。";
        return new AnalysisDimension(ruleId, title, "RISK",
                "存在验收项尚未被证据完整证明。", quote, basis, 1D, references);
    }

    private AnalysisDimension unknownDimension(String ruleId, String title, String basis) {
        return new AnalysisDimension(ruleId, title, "UNKNOWN", "现有资料不足以形成可靠结论。",
                "", basis, 1D, List.of());
    }

    private Issue validateIssue(Issue issue, Set<String> allowedRuleIds,
                                Map<String, SourceReference> sources) {
        if (issue == null || !allowedRuleIds.contains(trim(issue.ruleId()))
                || !StringUtils.hasText(issue.basis()) || !StringUtils.hasText(issue.quote())) {
            return null;
        }
        if ("SEM_RESULT_02".equals(trim(issue.ruleId()))) {
            return null;
        }
        List<SourceReference> cited = resolveAll(issue.references(), sources);
        if (cited.stream().anyMatch(source -> !relevantForRule(trim(issue.ruleId()), source.path()))) {
            return null;
        }
        String groundedQuote = verifiedQuote(issue.quote(), cited, false);
        if (cited.isEmpty() || !StringUtils.hasText(groundedQuote)) {
            return null;
        }
        String requestedField = trim(issue.field());
        String groundedField = cited.stream().map(SourceReference::path)
                .filter(path -> path.equals(requestedField))
                .findFirst()
                .orElse(cited.get(0).path());
        return new Issue(
                defaultText(issue.code(), "AI_SEMANTIC_RISK"),
                "AI",
                calibratedSeverity(trim(issue.ruleId())),
                groundedField,
                defaultText(issue.title(), "AI识别到语义风险"),
                trim(issue.ruleId()),
                groundedQuote,
                trim(issue.basis()),
                defaultText(issue.suggestion(), ""),
                confidence(issue.confidence()),
                cited.stream().map(this::safeLabel).distinct().toList()
        );
    }

    private List<AcceptanceCoverage> validateCoverage(Map<String, Object> businessData,
                                                       List<AcceptanceCoverage> rawCoverage,
                                                       Map<String, SourceReference> sources) {
        Map<String, String> criteria = acceptanceCriteria(businessData);
        if (criteria.isEmpty()) {
            return List.of();
        }
        Map<String, AcceptanceCoverage> rawByCriterion = new LinkedHashMap<>();
        if (rawCoverage != null) {
            for (AcceptanceCoverage item : rawCoverage) {
                if (item != null && criteria.containsKey(trim(item.criterionId()))) {
                    rawByCriterion.putIfAbsent(trim(item.criterionId()), item);
                }
            }
        }
        List<AcceptanceCoverage> result = new ArrayList<>();
        for (Map.Entry<String, String> criterion : criteria.entrySet()) {
            AcceptanceCoverage raw = rawByCriterion.get(criterion.getKey());
            if (raw == null || !StringUtils.hasText(raw.basis())) {
                result.add(unknownCoverage(criterion.getKey(), criterion.getValue(),
                        "模型未返回可核验的逐项判断。"));
                continue;
            }
            String status = normalizeCoverage(raw.status());
            List<SourceReference> cited = resolveAll(raw.evidenceReferences(), sources);
            boolean requiresEvidence = !"UNKNOWN".equals(status);
            if (requiresEvidence && (cited.isEmpty() || !StringUtils.hasText(raw.evidenceQuote())
                    || cited.stream().anyMatch(source -> !source.path().startsWith("businessData.evidence.extractedText"))
                    || !appearsInAny(raw.evidenceQuote(), cited))) {
                result.add(unknownCoverage(criterion.getKey(), criterion.getValue(),
                        "模型给出的证据引用未通过原文真实性校验。"));
                continue;
            }
            result.add(new AcceptanceCoverage(
                    criterion.getKey(),
                    criterion.getValue(),
                    status,
                    trim(raw.basis()),
                    requiresEvidence ? trim(raw.evidenceQuote()) : "",
                    confidence(raw.confidence()),
                    cited.stream().map(this::safeLabel).distinct().toList()
            ));
        }
        return result;
    }

    private AcceptanceCoverage unknownCoverage(String id, String criterion, String basis) {
        return new AcceptanceCoverage(id, criterion, "UNKNOWN", basis, "", 1D, List.of());
    }

    private Map<String, String> acceptanceCriteria(Map<String, Object> businessData) {
        Map<String, String> result = new LinkedHashMap<>();
        if (businessData == null || !(businessData.get("acceptanceCriteria") instanceof Collection<?> values)) {
            return result;
        }
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> item)) {
                continue;
            }
            String id = stringValue(item.get("id"));
            String text = stringValue(item.get("text"));
            if (StringUtils.hasText(id) && StringUtils.hasText(text)) {
                result.putIfAbsent(id, text);
            }
        }
        return result;
    }

    private Set<String> allowedRuleIds(List<Map<String, Object>> rules) {
        Set<String> result = new LinkedHashSet<>();
        if (rules == null) {
            return result;
        }
        for (Map<String, Object> rule : rules) {
            String id = rule == null ? "" : stringValue(rule.get("id"));
            if (StringUtils.hasText(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private Map<String, String> allowedRules(List<Map<String, Object>> rules) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rules == null) {
            return result;
        }
        for (Map<String, Object> rule : rules) {
            String id = rule == null ? "" : stringValue(rule.get("id"));
            String text = rule == null ? "" : stringValue(rule.get("text"));
            String title = rule == null ? "" : stringValue(rule.get("title"));
            if (StringUtils.hasText(id)) {
                result.putIfAbsent(id, defaultText(title, defaultText(text, id)));
            }
        }
        return result;
    }

    private Map<String, SourceReference> sourcesById(List<SourceReference> sourceCatalog) {
        Map<String, SourceReference> result = new LinkedHashMap<>();
        if (sourceCatalog != null) {
            for (SourceReference source : sourceCatalog) {
                if (source != null && StringUtils.hasText(source.id())) {
                    result.put(source.id(), source);
                }
            }
        }
        return result;
    }

    private List<SourceReference> resolveAll(List<String> referenceIds,
                                             Map<String, SourceReference> sources) {
        if (referenceIds == null || referenceIds.isEmpty()) {
            return List.of();
        }
        List<SourceReference> result = new ArrayList<>();
        for (String referenceId : referenceIds) {
            SourceReference source = sources.get(trim(referenceId));
            if (source == null) {
                return List.of();
            }
            result.add(source);
        }
        return result;
    }

    private boolean appearsInAny(String quote, List<SourceReference> sources) {
        String normalizedQuote = normalizeForMatch(quote);
        if (!StringUtils.hasText(normalizedQuote)) {
            return false;
        }
        return sources.stream().anyMatch(source -> {
            String original = normalizeForMatch(source.content());
            String redacted = normalizeForMatch(redactionService.redactText(source.content()));
            return original.contains(normalizedQuote) || redacted.contains(normalizedQuote);
        });
    }

    private String safeLabel(SourceReference source) {
        return redactionService.redactText(source == null ? "" : source.label());
    }

    private String verifiedQuote(String quote, List<SourceReference> sources, boolean allowPassFallback) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        if (StringUtils.hasText(quote) && appearsInAny(quote, sources)) {
            return trim(quote);
        }
        String normalizedQuote = normalizeForMatch(quote);
        if (StringUtils.hasText(normalizedQuote)) {
            SourceReference embedded = sources.stream()
                    .filter(source -> StringUtils.hasText(source.content()))
                    .filter(source -> embeddedSourceMatch(normalizedQuote,
                            normalizeForMatch(redactionService.redactText(source.content()))))
                    .max(java.util.Comparator.comparingInt(source -> redactionService.redactText(source.content()).length()))
                    .orElse(null);
            if (embedded != null) {
                return trim(redactionService.redactText(embedded.content()));
            }
        }
        if (!allowPassFallback) {
            return "";
        }
        SourceReference preferred = sources.stream()
                .filter(source -> source.path().endsWith(".taskContent")
                        || source.path().endsWith(".content")
                        || source.path().endsWith(".deliverable"))
                .findFirst()
                .orElse(sources.get(0));
        String content = trim(redactionService.redactText(preferred.content()));
        return content.length() <= 120 ? content : content.substring(0, 120);
    }

    private boolean embeddedSourceMatch(String normalizedQuote, String normalizedContent) {
        if (!StringUtils.hasText(normalizedContent) || normalizedContent.length() < 2) {
            return false;
        }
        if (normalizedContent.matches("[-+]?\\d+(\\.\\d+)?")) {
            Pattern numeric = Pattern.compile("(?<![\\d.])" + Pattern.quote(normalizedContent) + "(?![\\d.])");
            return numeric.matcher(normalizedQuote).find();
        }
        return normalizedQuote.contains(normalizedContent);
    }

    private void collectSources(Object value, String path, List<SourceReference> result) {
        if (value == null) {
            return;
        }
        if (path.endsWith(".instruction")) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                collectSources(entry.getValue(), path + "." + entry.getKey(), result);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                collectSources(item, path + "[" + index++ + "]", result);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                collectSources(Array.get(value, index), path + "[" + index + "]", result);
            }
            return;
        }
        String content = scalarContent(value);
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (path.endsWith(".evidence.extractedText") && addEvidenceSections(content, path, result)) {
            return;
        }
        addSource(path, path, content, result);
    }

    private boolean addEvidenceSections(String content, String path, List<SourceReference> result) {
        Matcher matcher = EVIDENCE_MARKER.matcher(content);
        List<Marker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new Marker(matcher.start(), matcher.end(), matcher.group(1)));
        }
        if (markers.isEmpty()) {
            return false;
        }
        for (int index = 0; index < markers.size(); index++) {
            Marker marker = markers.get(index);
            int end = index + 1 < markers.size() ? markers.get(index + 1).start() : content.length();
            String section = content.substring(marker.end(), end).trim();
            if (StringUtils.hasText(section)) {
                addSource(path + "#" + (index + 1), marker.label(), section, result);
            }
        }
        return true;
    }

    private void addSource(String path, String label, String content, List<SourceReference> result) {
        String id = String.format(Locale.ROOT, "SRC_%04d", result.size() + 1);
        result.add(new SourceReference(id, path, humanLabel(path, label), content.trim()));
    }

    private boolean coversRequiredSources(String ruleId, AnalysisRequest request,
                                           List<SourceReference> cited) {
        if ("SEM_WEEK_04".equals(ruleId) && request.sourceCatalog().stream()
                .noneMatch(source -> otherWeekItemField(source.path(), "content"))) {
            return false;
        }
        if ("SEM_DAY_04".equals(ruleId) && request.sourceCatalog().stream()
                .noneMatch(source -> source.path().matches("businessData\\.recentDayPlans\\[\\d+].content"))) {
            return false;
        }
        Set<String> citedPaths = cited.stream().map(SourceReference::path).collect(java.util.stream.Collectors.toSet());
        List<String> requiredPaths = request.sourceCatalog().stream()
                .map(SourceReference::path)
                .filter(path -> requiredForRule(ruleId, path))
                .toList();
        return requiredPaths.isEmpty() || citedPaths.containsAll(requiredPaths);
    }

    private boolean requiredForRule(String ruleId, String path) {
        return switch (ruleId) {
            case "SEM_PLAN_01" -> planField(path, "taskContent");
            case "SEM_PLAN_02" -> planField(path, "taskContent") || planField(path, "deliverable");
            case "SEM_PLAN_03" -> planField(path, "taskContent") || planField(path, "deadline")
                    || planField(path, "performanceWeight");
            case "SEM_PLAN_04" -> planField(path, "taskContent")
                    || path.matches("businessData\\.existingMonthTasks\\[\\d+].taskContent");
            case "SEM_EXTRA_01" -> path.equals("businessData.extraTask.taskContent")
                    || path.matches("businessData\\.existingMonthTasks\\[\\d+].taskContent");
            case "SEM_EXTRA_02" -> extraOrExistingField(path, "taskContent")
                    || extraOrExistingField(path, "deadline") || extraOrExistingField(path, "performanceWeight");
            case "SEM_WEEK_01" -> weekItemField(path, "content") || parentMonthField(path, "taskContent");
            case "SEM_WEEK_02" -> weekItemField(path, "deliverable") || parentMonthField(path, "deliverable");
            case "SEM_WEEK_03" -> weekItemField(path, "content") || weekItemField(path, "plannedFinishDate");
            case "SEM_WEEK_04" -> weekItemField(path, "content") || otherWeekItemField(path, "content");
            case "SEM_WEEK_05" -> weekItemField(path, "content") || parentMonthField(path, "taskContent")
                    || parentMonthField(path, "deliverable");
            case "SEM_DAY_01", "SEM_DAY_03" -> path.equals("businessData.dayPlan.content");
            case "SEM_DAY_02" -> path.equals("businessData.dayPlan.content")
                    || path.equals("businessData.parentMonthItem.taskContent")
                    || path.equals("businessData.correspondingWeekItem.content");
            case "SEM_DAY_04" -> path.equals("businessData.dayPlan.content")
                    || path.matches("businessData\\.recentDayPlans\\[\\d+].content");
            default -> false;
        };
    }

    private boolean relevantForRule(String ruleId, String path) {
        if (requiredForRule(ruleId, path)) {
            return true;
        }
        boolean evidenceText = path.startsWith("businessData.evidence.extractedText#")
                || path.equals("businessData.evidence.extractedText");
        boolean planContent = path.matches("businessData\\.planItem\\.(taskName|taskContent|deliverable)")
                || path.matches("businessData\\.monthPlan\\.(title|summary)");
        boolean criterion = path.matches("businessData\\.acceptanceCriteria\\[\\d+]\\.(id|text)");
        return switch (ruleId) {
            case "SEM_RESULT_01" -> evidenceText || planContent;
            case "SEM_RESULT_02" -> evidenceText || criterion;
            case "SEM_RESULT_03" -> evidenceText || path.equals("businessData.result.description");
            case "SEM_RESULT_04" -> evidenceText || criterion;
            case "SEM_RESULT_05" -> path.startsWith("businessData.organizationStandards[");
            default -> false;
        };
    }

    private boolean conflictsWithSubstantiveRisk(String ruleId, List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return false;
        }
        return issues.stream()
                .filter(issue -> issue != null && Set.of("MEDIUM", "HIGH", "BLOCKING")
                        .contains(defaultText(issue.severity(), "LOW").toUpperCase(Locale.ROOT)))
                .map(Issue::ruleId)
                .filter(StringUtils::hasText)
                .anyMatch(riskRuleId -> riskInvalidatesPass(riskRuleId, ruleId));
    }

    private boolean riskInvalidatesPass(String riskRuleId, String passRuleId) {
        if (riskRuleId.equals(passRuleId)) {
            return true;
        }
        return switch (riskRuleId) {
            case "SEM_PLAN_01" -> Set.of("SEM_PLAN_02", "SEM_PLAN_03", "SEM_PLAN_04")
                    .contains(passRuleId);
            case "SEM_WEEK_01" -> Set.of("SEM_WEEK_03", "SEM_WEEK_05").contains(passRuleId);
            case "SEM_DAY_01" -> Set.of("SEM_DAY_02", "SEM_DAY_03", "SEM_DAY_04").contains(passRuleId);
            case "SEM_RESULT_01" -> Set.of("SEM_RESULT_02", "SEM_RESULT_03", "SEM_RESULT_04")
                    .contains(passRuleId);
            case "SEM_RESULT_04" -> Set.of("SEM_RESULT_02", "SEM_RESULT_03").contains(passRuleId);
            default -> false;
        };
    }

    private boolean planField(String path, String field) {
        return path.matches("businessData\\.items\\[\\d+]." + field)
                || path.equals("businessData.extraTask." + field);
    }

    private boolean extraOrExistingField(String path, String field) {
        return path.equals("businessData.extraTask." + field)
                || path.matches("businessData\\.existingMonthTasks\\[\\d+]." + field);
    }

    private boolean weekItemField(String path, String field) {
        return path.matches("businessData\\.itemsWithParents\\[\\d+]\\.weekItem\\." + field);
    }

    private boolean parentMonthField(String path, String field) {
        return path.matches("businessData\\.itemsWithParents\\[\\d+]\\.parentMonthItem\\." + field);
    }

    private boolean otherWeekItemField(String path, String field) {
        return path.matches("businessData\\.otherWeeksForSameMonthItems\\[\\d+]\\.items\\[\\d+]\\." + field);
    }

    private String humanLabel(String path, String fallback) {
        if (path != null && path.startsWith("businessData.evidence.extractedText#")) {
            return fallback;
        }
        if (path == null) return fallback;
        Matcher weekNested = Pattern.compile("businessData\\.itemsWithParents\\[(\\d+)]\\.(weekItem|parentMonthItem)\\.([A-Za-z]+)").matcher(path);
        if (weekNested.matches()) {
            int index = Integer.parseInt(weekNested.group(1)) + 1;
            String object = "weekItem".equals(weekNested.group(2)) ? "周计划" : "父级月计划";
            return "第" + index + "项" + object + " · " + fieldLabel(weekNested.group(3));
        }
        Matcher otherWeekNested = Pattern.compile("businessData\\.otherWeeksForSameMonthItems\\[(\\d+)]\\.items\\[(\\d+)]\\.([A-Za-z]+)").matcher(path);
        if (otherWeekNested.matches()) {
            int weekIndex = Integer.parseInt(otherWeekNested.group(1)) + 1;
            int itemIndex = Integer.parseInt(otherWeekNested.group(2)) + 1;
            return "对比周计划" + weekIndex + " · 第" + itemIndex + "项 · " + fieldLabel(otherWeekNested.group(3));
        }
        Matcher indexed = Pattern.compile("businessData\\.([A-Za-z]+)\\[(\\d+)]\\.([A-Za-z]+)").matcher(path);
        if (indexed.matches()) {
            int index = Integer.parseInt(indexed.group(2)) + 1;
            return collectionLabel(indexed.group(1)) + "第" + index + "项 · " + fieldLabel(indexed.group(3));
        }
        Matcher direct = Pattern.compile("businessData\\.([A-Za-z]+)\\.([A-Za-z]+)").matcher(path);
        if (direct.matches()) {
            return objectLabel(direct.group(1)) + " · " + fieldLabel(direct.group(2));
        }
        return path.replace("businessData.", "").replace(".", " · ");
    }

    private String collectionLabel(String value) {
        return switch (value) {
            case "items" -> "当前计划";
            case "existingMonthTasks" -> "原月计划";
            case "parentMonthItems" -> "父级月计划";
            case "recentDayPlans" -> "近期日计划";
            case "relatedWeekPlans" -> "相关周计划";
            case "organizationStandards" -> "部门参考标准";
            case "acceptanceCriteria" -> "验收项";
            default -> value;
        };
    }

    private String objectLabel(String value) {
        return switch (value) {
            case "plan" -> "当前计划";
            case "dayPlan" -> "当前日计划";
            case "weekPlan" -> "当前周计划";
            case "extraTask" -> "当前额外任务";
            case "approvedMonthPlan" -> "已审批月计划";
            case "monthPlan" -> "月计划";
            case "planItem" -> "计划任务";
            case "result" -> "成果申报";
            case "evidence" -> "成果证据";
            case "parentMonthItem" -> "父级月计划任务";
            case "correspondingWeekItem" -> "关联周计划任务";
            case "workdayRule" -> "工作日规则";
            default -> value;
        };
    }

    private String fieldLabel(String value) {
        return switch (value) {
            case "title" -> "标题";
            case "summary" -> "计划摘要";
            case "planMonth" -> "计划月份";
            case "taskName" -> "任务名称";
            case "taskContent", "content" -> "任务内容";
            case "deliverable" -> "交付物";
            case "deadline" -> "截止日期";
            case "performanceWeight" -> "绩效权重";
            case "plannedFinishDate" -> "计划完成日期";
            case "remark" -> "备注";
            case "status" -> "状态";
            case "weekStart" -> "周开始日期";
            case "weekEnd" -> "周结束日期";
            case "planDate" -> "计划日期";
            case "description" -> "说明";
            case "text" -> "要求";
            case "templateName" -> "模板名称";
            case "standard" -> "验收标准";
            default -> value;
        };
    }

    private String scalarContent(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return String.valueOf(value).trim();
        }
        return "";
    }

    private String normalizeForMatch(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String calibratedSeverity(String ruleId) {
        return switch (ruleId) {
            case "SEM_PLAN_03", "SEM_EXTRA_01", "SEM_EXTRA_02", "SEM_WEEK_03",
                    "SEM_RESULT_01", "SEM_RESULT_02", "SEM_RESULT_03", "SEM_RESULT_04" -> "HIGH";
            default -> "MEDIUM";
        };
    }

    private String normalizeCoverage(String value) {
        String normalized = defaultText(value, "UNKNOWN").toUpperCase(Locale.ROOT);
        return Set.of("PROVEN", "PARTIAL", "UNPROVEN", "UNKNOWN").contains(normalized)
                ? normalized : "UNKNOWN";
    }

    private String normalizeDimensionStatus(String value) {
        String normalized = defaultText(value, "UNKNOWN").toUpperCase(Locale.ROOT);
        return Set.of("PASS", "RISK", "UNKNOWN").contains(normalized) ? normalized : "UNKNOWN";
    }

    private double confidence(Double value) {
        return value == null ? 0.5D : Math.max(0D, Math.min(1D, value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private ModelAnalysis emptyAnalysis() {
        return new ModelAnalysis("千问未返回可核验结论。", List.of(), List.of(), List.of());
    }

    private record Marker(int start, int end, String label) {
    }
}
