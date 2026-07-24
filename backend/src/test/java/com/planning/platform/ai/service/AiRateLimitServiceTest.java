package com.planning.platform.ai.service;

import com.planning.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRateLimitServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock AiRepository repository;

    @Test
    void rejectsCallAboveRedisLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(11L);
        AiRateLimitService service = new AiRateLimitService(redisTemplate, repository);

        assertThatThrownBy(() -> service.consume(10L, "MONTH_PLAN_DRAFT", 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void fallsBackToPersistedUsageWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(repository.todaySuccessfulCalls(10L, "MONTH_PLAN_DRAFT")).thenReturn(10);
        AiRateLimitService service = new AiRateLimitService(redisTemplate, repository);

        assertThatThrownBy(() -> service.consume(10L, "MONTH_PLAN_DRAFT", 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void reviewLimitUsesCombinedPersistedUsageAcrossReviewTypes() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(repository.todaySuccessfulReviewCalls(10L)).thenReturn(20);
        AiRateLimitService service = new AiRateLimitService(redisTemplate, repository);

        assertThatThrownBy(() -> service.consume(10L, AiRateLimitService.AI_REVIEW_CHECK, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void persistedUsageStillEnforcesLimitAfterRedisCounterReset() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(repository.todaySuccessfulReviewCalls(10L)).thenReturn(20);
        AiRateLimitService service = new AiRateLimitService(redisTemplate, repository);

        assertThatThrownBy(() -> service.consume(10L, AiRateLimitService.AI_REVIEW_CHECK, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void legacyMonthPlanCheckSharesTheCombinedReviewQuota() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(repository.todaySuccessfulReviewCalls(10L)).thenReturn(20);
        AiRateLimitService service = new AiRateLimitService(redisTemplate, repository);

        assertThatThrownBy(() -> service.consume(10L, "MONTH_PLAN_CHECK", 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");
    }
}
