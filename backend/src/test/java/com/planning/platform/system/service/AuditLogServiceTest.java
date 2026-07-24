package com.planning.platform.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceTest {

    private final SysAuditLogMapper mapper = mock(SysAuditLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditLogService service = new AuditLogService(mapper, objectMapper);
    private final AuthUser user = new AuthUser(10L, "employee", "员工", 110L, 20L,
            false, List.of("EMPLOYEE"), List.of());

    @Test
    void preservesValidJsonDetail() throws Exception {
        service.success(user, "ACTION", "RESULT", 1L, "{\"status\":\"SUCCESS\"}");

        SysAuditLog log = capturedLog();
        assertThat(objectMapper.readTree(log.getDetail()).path("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void wrapsPlainTextAsValidJson() throws Exception {
        service.success(user, "ACTION", "RESULT", 1L, "appealNo=AP1, evidenceCount=1");

        SysAuditLog log = capturedLog();
        JsonNode detail = objectMapper.readTree(log.getDetail());
        assertThat(detail.path("detail").asText()).contains("appealNo=AP1", "evidenceCount=1");
    }

    private SysAuditLog capturedLog() {
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }
}
