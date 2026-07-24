package com.planning.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiAdminModels;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiRepository {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Optional<ModelConfig> activeConfig() {
        List<ModelConfig> rows = jdbcTemplate.query("""
                SELECT * FROM ai_model_config
                WHERE deleted = 0 AND status = 'ENABLED'
                ORDER BY id DESC LIMIT 1
                """, modelConfigMapper());
        return rows.stream().findFirst();
    }

    public ModelConfig requireActiveConfig() {
        return activeConfig().orElseThrow(() -> new BizException(503, "AI 功能尚未配置"));
    }

    public List<ModelConfig> modelConfigs() {
        return jdbcTemplate.query("""
                SELECT * FROM ai_model_config
                WHERE deleted = 0
                ORDER BY CASE WHEN status = 'ENABLED' THEN 0 ELSE 1 END, updated_at DESC, id DESC
                """, modelConfigMapper());
    }

    public ModelConfig requireModelConfig(Long id) {
        List<ModelConfig> rows = jdbcTemplate.query("""
                SELECT * FROM ai_model_config WHERE id = ? AND deleted = 0
                """, modelConfigMapper(), id);
        return rows.stream().findFirst()
                .orElseThrow(() -> new BizException(404, "AI 模型配置不存在"));
    }

    public PromptTemplate requirePrompt(String sceneCode) {
        List<PromptTemplate> rows = jdbcTemplate.query("""
                SELECT * FROM ai_prompt_template
                WHERE scene_code = ? AND status = 'ENABLED' AND deleted = 0
                ORDER BY id DESC LIMIT 1
                """, promptMapper(), sceneCode);
        return rows.stream().findFirst()
                .orElseThrow(() -> new BizException(503, "AI 场景 Prompt 尚未配置：" + sceneCode));
    }

    public Optional<String> successfulOutput(Long userId, String sceneCode, String requestId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT output_json FROM ai_call_log
                WHERE user_id = ? AND scene_code = ? AND request_id = ? AND success = 1
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), userId, sceneCode, requestId);
        return rows.stream().findFirst();
    }

    public String newSuggestionId() {
        return "AI" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public void saveSuccess(CallRecord record) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log
                      (suggestion_id, request_id, scene_code, user_id, org_id, biz_type, biz_id,
                       input_hash, input_summary, provider_code, model_name, prompt_version, output_json,
                       input_tokens, output_tokens, latency_ms, success)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      suggestion_id = IF(success = 0, VALUES(suggestion_id), suggestion_id),
                      input_hash = IF(success = 0, VALUES(input_hash), input_hash),
                      input_summary = IF(success = 0, VALUES(input_summary), input_summary),
                      provider_code = IF(success = 0, VALUES(provider_code), provider_code),
                      model_name = IF(success = 0, VALUES(model_name), model_name),
                      prompt_version = IF(success = 0, VALUES(prompt_version), prompt_version),
                      output_json = IF(success = 0, VALUES(output_json), output_json),
                      input_tokens = IF(success = 0, VALUES(input_tokens), input_tokens),
                      output_tokens = IF(success = 0, VALUES(output_tokens), output_tokens),
                      latency_ms = IF(success = 0, VALUES(latency_ms), latency_ms),
                      error_code = IF(success = 0, NULL, error_code),
                      error_message = IF(success = 0, NULL, error_message),
                      success = 1
                    """, record.suggestionId(), record.requestId(), record.sceneCode(), record.userId(),
                    record.orgId(), record.bizType(), record.bizId(), record.inputHash(), record.inputSummary(),
                    record.providerCode(), record.modelName(), record.promptVersion(), record.outputJson(),
                    record.inputTokens(), record.outputTokens(), record.latencyMs());
        } catch (DuplicateKeyException ignored) {
            // suggestion_id 与其他请求冲突的极低概率保护。
        }
    }

    public void saveFailure(CallRecord record, String errorCode, String errorMessage) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_call_log
                      (suggestion_id, request_id, scene_code, user_id, org_id, biz_type, biz_id,
                       input_hash, input_summary, provider_code, model_name, prompt_version,
                       input_tokens, output_tokens, latency_ms, success, error_code, error_message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, record.suggestionId(), record.requestId(), record.sceneCode(), record.userId(),
                    record.orgId(), record.bizType(), record.bizId(), record.inputHash(), record.inputSummary(),
                    record.providerCode(), record.modelName(), record.promptVersion(), record.inputTokens(),
                    record.outputTokens(), record.latencyMs(), shorten(errorCode, 60), shorten(errorMessage, 500));
        } catch (DuplicateKeyException ignored) {
            // 幂等键已存在时不覆盖首次结果。
        }
    }

    public int todaySuccessfulCalls(Long userId, String sceneCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_call_log
                WHERE user_id = ? AND scene_code = ? AND success = 1 AND created_at >= CURRENT_DATE()
                """, Integer.class, userId, sceneCode);
        return count == null ? 0 : count;
    }

    public int todaySuccessfulReviewCalls(Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_call_log
                WHERE user_id = ?
                  AND (scene_code LIKE 'AI_REVIEW_%' OR scene_code = 'MONTH_PLAN_CHECK')
                  AND success = 1
                  AND created_at >= CURRENT_DATE()
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    public void saveAction(AuthUser user, String suggestionId, AiModels.SuggestionActionRequest request) {
        Integer owned = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_call_log
                WHERE suggestion_id = ? AND user_id = ? AND success = 1
                """, Integer.class, suggestionId, user.userId());
        if (owned == null || owned == 0) {
            throw new BizException(404, "AI 建议不存在或不属于当前员工");
        }
        String action = request.actionCode().toUpperCase();
        if (!List.of("PREVIEW", "APPLY_ALL", "APPLY_ITEM", "APPLY_FIELDS", "ADOPT_WITH_EDIT", "IGNORE").contains(action)) {
            throw new BizException(422, "不支持的 AI 建议动作");
        }
        jdbcTemplate.update("""
                INSERT INTO ai_suggestion_action
                  (suggestion_id, user_id, action_code, applied_fields, before_hash, after_hash)
                VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                """, suggestionId, user.userId(), action, toJson(request.appliedFields() == null ? List.of() : request.appliedFields()),
                request.beforeHash(), request.afterHash());
    }

    public Optional<PlanContext> planContext(Long orgId, String planMonth) {
        List<PlanContext> rows = jdbcTemplate.query("""
                SELECT * FROM ai_plan_context
                WHERE org_id = ? AND plan_month = ? AND deleted = 0 AND status = 'ENABLED'
                LIMIT 1
                """, planContextMapper(), orgId, planMonth);
        return rows.stream().findFirst();
    }

    @Transactional
    public PlanContext savePlanContext(AuthUser user, AiModels.SavePlanContextRequest request) {
        if (request.versionNo() == 0) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO ai_plan_context
                          (org_id, plan_month, department_goal, leader_requirement, version_no, status, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 1, 'ENABLED', ?, ?)
                        """, request.orgId(), request.planMonth(), blankToNull(request.departmentGoal()),
                        blankToNull(request.leaderRequirement()), user.userId(), user.userId());
            } catch (DuplicateKeyException ex) {
                throw new BizException(409, "本月计划要求已被其他人创建，请刷新后重试");
            }
        } else {
            int updated = jdbcTemplate.update("""
                    UPDATE ai_plan_context
                    SET department_goal = ?, leader_requirement = ?, version_no = version_no + 1,
                        updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE org_id = ? AND plan_month = ? AND version_no = ? AND deleted = 0
                    """, blankToNull(request.departmentGoal()), blankToNull(request.leaderRequirement()),
                    user.userId(), request.orgId(), request.planMonth(), request.versionNo());
            if (updated == 0) {
                throw new BizException(409, "本月计划要求已被其他人修改，请刷新后重试");
            }
        }
        return planContext(request.orgId(), request.planMonth()).orElseThrow();
    }

    public List<AiAdminModels.ModelConfigResponse> configResponses() {
        return modelConfigs().stream().map(ModelConfig::toResponse).toList();
    }

    @Transactional
    public ModelConfig createConfig(AuthUser user, AiAdminModels.SaveModelConfigRequest request, String encryptedApiKey) {
        jdbcTemplate.update("""
                INSERT INTO ai_model_config
                  (config_name, provider_code, base_url, api_key_ciphertext, model_name, timeout_seconds,
                   global_enabled, draft_enabled, optimize_enabled, check_enabled, allowed_user_ids,
                   allowed_org_ids, draft_daily_limit, optimize_daily_limit, check_daily_limit,
                   status, version_no, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DISABLED', 1, ?, ?)
                """, request.configName(), request.providerCode().toUpperCase(), blankToNull(request.baseUrl()),
                encryptedApiKey, request.modelName(), request.timeoutSeconds(), request.globalEnabled(),
                request.draftEnabled(), request.optimizeEnabled(), request.checkEnabled(),
                normalizeIds(request.allowedUserIds()), normalizeIds(request.allowedOrgIds()), request.draftDailyLimit(),
                request.optimizeDailyLimit(), request.checkDailyLimit(), user.userId(), user.userId());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return requireModelConfig(id);
    }

    @Transactional
    public ModelConfig updateConfig(Long id, AuthUser user, AiAdminModels.SaveModelConfigRequest request,
                                    String encryptedApiKey) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_model_config SET
                  config_name = ?, provider_code = ?, base_url = ?, api_key_ciphertext = ?, model_name = ?,
                  timeout_seconds = ?, global_enabled = ?, draft_enabled = ?, optimize_enabled = ?, check_enabled = ?,
                  allowed_user_ids = ?, allowed_org_ids = ?, draft_daily_limit = ?, optimize_daily_limit = ?,
                  check_daily_limit = ?, version_no = version_no + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version_no = ? AND deleted = 0
                """, request.configName(), request.providerCode().toUpperCase(), blankToNull(request.baseUrl()),
                encryptedApiKey, request.modelName(), request.timeoutSeconds(), request.globalEnabled(),
                request.draftEnabled(), request.optimizeEnabled(), request.checkEnabled(),
                normalizeIds(request.allowedUserIds()), normalizeIds(request.allowedOrgIds()), request.draftDailyLimit(),
                request.optimizeDailyLimit(), request.checkDailyLimit(), user.userId(), id, request.versionNo());
        if (updated == 0) throw new BizException(409, "AI 配置已被其他人修改，请刷新后重试");
        return requireModelConfig(id);
    }

    @Transactional
    public ModelConfig enableConfig(Long id, AuthUser user) {
        requireModelConfig(id);
        jdbcTemplate.queryForList("SELECT id FROM ai_model_config WHERE deleted = 0 FOR UPDATE", Long.class);
        jdbcTemplate.update("""
                UPDATE ai_model_config SET status = 'DISABLED', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE deleted = 0 AND status = 'ENABLED' AND id <> ?
                """, user.userId(), id);
        jdbcTemplate.update("""
                UPDATE ai_model_config SET status = 'ENABLED', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted = 0 AND status <> 'ENABLED'
                """, user.userId(), id);
        return requireModelConfig(id);
    }

    public List<AiAdminModels.PromptResponse> prompts() {
        return jdbcTemplate.query("""
                SELECT * FROM ai_prompt_template WHERE deleted = 0 ORDER BY scene_code, id DESC
                """, (rs, rowNum) -> new AiAdminModels.PromptResponse(
                rs.getLong("id"), rs.getString("scene_code"), rs.getString("version_no"),
                rs.getString("system_prompt"), rs.getString("user_template"),
                rs.getString("output_schema_version"), rs.getString("status"),
                display(rs.getTimestamp("created_at").toLocalDateTime())));
    }

    @Transactional
    public AiAdminModels.PromptResponse publishPrompt(AuthUser user, AiAdminModels.PublishPromptRequest request) {
        if (!List.of(AiModels.MONTH_PLAN_DRAFT, AiModels.MONTH_PLAN_ITEM_OPTIMIZE, AiModels.MONTH_PLAN_CHECK)
                .contains(request.sceneCode())) {
            throw new BizException(422, "不支持的 Prompt 场景");
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_prompt_template WHERE scene_code = ?",
                Integer.class, request.sceneCode());
        String version = "v" + ((count == null ? 0 : count) + 1);
        jdbcTemplate.update("UPDATE ai_prompt_template SET status = 'ARCHIVED' WHERE scene_code = ? AND status = 'ENABLED'",
                request.sceneCode());
        jdbcTemplate.update("""
                INSERT INTO ai_prompt_template
                  (scene_code, version_no, system_prompt, user_template, output_schema_version, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """, request.sceneCode(), version, request.systemPrompt(), request.userTemplate(),
                request.outputSchemaVersion(), user.userId(), user.userId());
        return prompts().stream().filter(item -> item.sceneCode().equals(request.sceneCode()) && item.versionNo().equals(version))
                .findFirst().orElseThrow();
    }

    public AiAdminModels.MetricsResponse metrics(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 365)));
        Map<String, Object> total = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) total_calls,
                       COALESCE(SUM(success), 0) success_calls,
                       COALESCE(SUM(input_tokens), 0) input_tokens,
                       COALESCE(SUM(output_tokens), 0) output_tokens,
                       COALESCE(AVG(latency_ms), 0) average_latency
                FROM ai_call_log WHERE created_at >= ?
                """, from);
        Long adopted = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT suggestion_id) FROM ai_suggestion_action
                WHERE action_code IN ('APPLY_ALL','APPLY_ITEM','APPLY_FIELDS','ADOPT_WITH_EDIT') AND created_at >= ?
                """, Long.class, from);
        List<Map<String, Object>> byScene = jdbcTemplate.query("""
                SELECT scene_code, COUNT(*) calls, COALESCE(SUM(success), 0) successes,
                       COALESCE(SUM(input_tokens + output_tokens), 0) tokens,
                       COALESCE(AVG(latency_ms), 0) average_latency_ms
                FROM ai_call_log WHERE created_at >= ? GROUP BY scene_code ORDER BY scene_code
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sceneCode", rs.getString("scene_code"));
            row.put("calls", rs.getLong("calls"));
            row.put("successes", rs.getLong("successes"));
            row.put("tokens", rs.getLong("tokens"));
            row.put("averageLatencyMs", rs.getLong("average_latency_ms"));
            return row;
        }, from);
        long totalCalls = number(total.get("total_calls"));
        long successCalls = number(total.get("success_calls"));
        long adoptedCount = adopted == null ? 0 : adopted;
        return new AiAdminModels.MetricsResponse(totalCalls, successCalls,
                totalCalls == 0 ? 0 : round(successCalls * 100.0 / totalCalls),
                number(total.get("input_tokens")), number(total.get("output_tokens")),
                number(total.get("average_latency")), adoptedCount,
                successCalls == 0 ? 0 : round(adoptedCount * 100.0 / successCalls), byScene);
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "AI 数据序列化失败");
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "AI 历史结果解析失败");
        }
    }

    private RowMapper<ModelConfig> modelConfigMapper() {
        return (rs, rowNum) -> new ModelConfig(
                rs.getLong("id"), rs.getString("config_name"), rs.getString("provider_code"),
                rs.getString("base_url"), rs.getString("api_key_ciphertext"), rs.getString("model_name"),
                rs.getInt("timeout_seconds"), rs.getBoolean("global_enabled"), rs.getBoolean("draft_enabled"),
                rs.getBoolean("optimize_enabled"), rs.getBoolean("check_enabled"), rs.getString("allowed_user_ids"),
                rs.getString("allowed_org_ids"), rs.getInt("draft_daily_limit"), rs.getInt("optimize_daily_limit"),
                rs.getInt("check_daily_limit"), rs.getInt("version_no"), rs.getString("status"));
    }

    private RowMapper<PromptTemplate> promptMapper() {
        return (rs, rowNum) -> new PromptTemplate(rs.getLong("id"), rs.getString("scene_code"),
                rs.getString("version_no"), rs.getString("system_prompt"), rs.getString("user_template"),
                rs.getString("output_schema_version"));
    }

    private RowMapper<PlanContext> planContextMapper() {
        return (rs, rowNum) -> new PlanContext(rs.getLong("id"), rs.getLong("org_id"),
                rs.getString("plan_month"), rs.getString("department_goal"), rs.getString("leader_requirement"),
                rs.getInt("version_no"), rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private String normalizeIds(String value) {
        if (!StringUtils.hasText(value)) return null;
        List<String> normalized = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.matches("\\d+")) throw new BizException(422, "灰度用户和组织只能填写逗号分隔的数字 ID");
            if (!normalized.contains(trimmed)) normalized.add(trimmed);
        }
        return String.join(",", normalized);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String shorten(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String display(LocalDateTime value) {
        return value == null ? "" : DISPLAY_TIME.format(value);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ModelConfig(
            Long id, String configName, String providerCode, String baseUrl, String apiKeyCiphertext,
            String modelName, int timeoutSeconds, boolean globalEnabled, boolean draftEnabled,
            boolean optimizeEnabled, boolean checkEnabled, String allowedUserIds, String allowedOrgIds,
            int draftDailyLimit, int optimizeDailyLimit, int checkDailyLimit, int versionNo, String status
    ) {
        public boolean sceneEnabled(String scene) {
            return switch (scene) {
                case AiModels.MONTH_PLAN_DRAFT, AiModels.WEEK_PLAN_DRAFT, AiModels.DAY_PLAN_DRAFT -> draftEnabled;
                case AiModels.MONTH_PLAN_ITEM_OPTIMIZE, AiModels.WEEK_PLAN_ADJUST, AiModels.DAY_PLAN_ADJUST -> optimizeEnabled;
                case AiModels.MONTH_PLAN_CHECK -> checkEnabled;
                default -> false;
            };
        }

        public int limitFor(String scene) {
            return switch (scene) {
                case AiModels.MONTH_PLAN_DRAFT, AiModels.WEEK_PLAN_DRAFT, AiModels.DAY_PLAN_DRAFT -> draftDailyLimit;
                case AiModels.MONTH_PLAN_ITEM_OPTIMIZE, AiModels.WEEK_PLAN_ADJUST, AiModels.DAY_PLAN_ADJUST -> optimizeDailyLimit;
                case AiModels.MONTH_PLAN_CHECK -> checkDailyLimit;
                default -> 0;
            };
        }

        public AiAdminModels.ModelConfigResponse toResponse() {
            return new AiAdminModels.ModelConfigResponse(id, configName, providerCode, baseUrl,
                    StringUtils.hasText(apiKeyCiphertext), modelName, timeoutSeconds, globalEnabled,
                    draftEnabled, optimizeEnabled, checkEnabled, allowedUserIds, allowedOrgIds,
                    draftDailyLimit, optimizeDailyLimit, checkDailyLimit, versionNo, status);
        }
    }

    public record PromptTemplate(Long id, String sceneCode, String versionNo, String systemPrompt,
                                 String userTemplate, String outputSchemaVersion) {
    }

    public record PlanContext(Long id, Long orgId, String planMonth, String departmentGoal,
                              String leaderRequirement, Integer versionNo, LocalDateTime updatedAt) {
    }

    public record CallRecord(
            String suggestionId, String requestId, String sceneCode, Long userId, Long orgId,
            String bizType, Long bizId, String inputHash, String inputSummary, String providerCode,
            String modelName, String promptVersion, String outputJson, int inputTokens, int outputTokens,
            long latencyMs
    ) {
    }
}
