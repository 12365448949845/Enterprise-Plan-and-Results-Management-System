package com.planning.platform.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiLogRetentionService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${planning.audit.retain-days:90}")
    private long retainDays;

    @Transactional
    @Scheduled(cron = "${planning.audit.cleanup-cron:0 40 3 * * *}")
    public int cleanupExpiredLogs() {
        if (retainDays < 1) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);
        int actions = jdbcTemplate.update("DELETE FROM ai_suggestion_action WHERE created_at < ?", cutoff);
        int calls = jdbcTemplate.update("DELETE FROM ai_call_log WHERE created_at < ?", cutoff);
        return actions + calls;
    }
}
