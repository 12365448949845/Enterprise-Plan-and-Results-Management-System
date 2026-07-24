package com.planning.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogRetentionServiceTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogRetentionService retentionService;

    @Test
    void deletesLogsOlderThanConfiguredRetentionPeriod() {
        ReflectionTestUtils.setField(retentionService, "retainDays", 90L);
        when(auditLogMapper.delete(any(Wrapper.class))).thenReturn(12);

        int deleted = retentionService.cleanupExpiredLogs();

        assertThat(deleted).isEqualTo(12);
        verify(auditLogMapper).delete(any(Wrapper.class));
    }

    @Test
    void disabledRetentionDoesNotDeleteLogs() {
        ReflectionTestUtils.setField(retentionService, "retainDays", 0L);

        assertThat(retentionService.cleanupExpiredLogs()).isZero();
        verify(auditLogMapper, never()).delete(any(Wrapper.class));
    }
}
