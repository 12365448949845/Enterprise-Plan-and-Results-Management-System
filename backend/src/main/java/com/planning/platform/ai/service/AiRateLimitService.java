package com.planning.platform.ai.service;

import com.planning.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AiRateLimitService {

    public static final String AI_REVIEW_CHECK = "AI_REVIEW_CHECK";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final StringRedisTemplate redisTemplate;
    private final AiRepository repository;

    public void consume(Long userId, String sceneCode, int limit) {
        String key = key(userId, sceneCode);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) redisTemplate.expire(key, untilTomorrow());
            long effectiveCount = Math.max(count == null ? 0L : count,
                    (long) persistedUsage(userId, sceneCode) + 1L);
            if (effectiveCount > limit) {
                throw new BizException(429, "今日 AI 调用次数已达上限");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException redisUnavailable) {
            if (persistedUsage(userId, sceneCode) >= limit) {
                throw new BizException(429, "今日 AI 调用次数已达上限");
            }
        }
    }

    public int remaining(Long userId, String sceneCode, int limit) {
        int used = persistedUsage(userId, sceneCode);
        try {
            String raw = redisTemplate.opsForValue().get(key(userId, sceneCode));
            if (raw != null) used = Math.max(used, Integer.parseInt(raw));
        } catch (RuntimeException ignored) {
        }
        return Math.max(0, limit - used);
    }

    private int persistedUsage(Long userId, String sceneCode) {
        return isCombinedCheckScene(sceneCode)
                ? repository.todaySuccessfulReviewCalls(userId)
                : repository.todaySuccessfulCalls(userId, sceneCode);
    }

    private String key(Long userId, String sceneCode) {
        String quotaScene = isCombinedCheckScene(sceneCode) ? AI_REVIEW_CHECK : sceneCode;
        return "planning:ai:limit:" + LocalDate.now(BUSINESS_ZONE) + ":" + userId + ":" + quotaScene;
    }

    private boolean isCombinedCheckScene(String sceneCode) {
        return AI_REVIEW_CHECK.equals(sceneCode) || "MONTH_PLAN_CHECK".equals(sceneCode);
    }

    private Duration untilTomorrow() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).plusMinutes(5);
    }
}
