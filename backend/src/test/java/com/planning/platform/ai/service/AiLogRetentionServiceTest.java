package com.planning.platform.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLogRetentionServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks AiLogRetentionService service;

    @Test
    void removesSuggestionActionsBeforeCallLogs() {
        ReflectionTestUtils.setField(service, "retainDays", 90L);
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(3, 7);

        assertThat(service.cleanupExpiredLogs()).isEqualTo(10);
    }

    @Test
    void disabledRetentionDoesNothing() {
        ReflectionTestUtils.setField(service, "retainDays", 0L);
        assertThat(service.cleanupExpiredLogs()).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(Object.class));
    }
}
