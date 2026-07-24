package com.planning.platform.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final ObjectMapper objectMapper;

    public void success(AuthUser user, String action, String targetType, Long targetId, String detail) {
        SysAuditLog log = new SysAuditLog();
        log.setUserId(user.userId());
        log.setUsername(user.username());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setResult("SUCCESS");
        log.setDetail(normalizeDetail(detail));
        sysAuditLogMapper.insert(log);
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(detail);
            return detail;
        } catch (JsonProcessingException ignored) {
            try {
                return objectMapper.writeValueAsString(Map.of("detail", detail));
            } catch (JsonProcessingException ex) {
                return "{\"detail\":\"unavailable\"}";
            }
        }
    }
}
