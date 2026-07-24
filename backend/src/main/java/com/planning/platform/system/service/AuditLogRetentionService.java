package com.planning.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogRetentionService {

    private final SysAuditLogMapper auditLogMapper;

    @Value("${planning.audit.retain-days:90}")
    private long retainDays;

    @Scheduled(cron = "${planning.audit.cleanup-cron:0 40 3 * * *}")
    public int cleanupExpiredLogs() {
        if (retainDays < 1) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);
        return auditLogMapper.delete(new LambdaQueryWrapper<SysAuditLog>()
                .lt(SysAuditLog::getCreatedAt, cutoff));
    }
}
