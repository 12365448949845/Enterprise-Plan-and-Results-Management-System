package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiAdminModels;
import com.planning.platform.ai.provider.AiProviderRegistry;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiManagementServiceTest {

    @Mock AiRepository repository;
    @Mock AiCryptoService cryptoService;
    @Mock AiProviderRegistry providerRegistry;
    @Mock ObjectMapper objectMapper;
    @Mock AuditLogService auditLogService;
    @InjectMocks AiManagementService service;

    private final AuthUser admin = new AuthUser(1L, "admin", "管理员", 1L, 1L,
            false, List.of("SYS_ADMIN"), List.of());

    @Test
    void createConfigReturnsDisabledConfiguration() {
        AiAdminModels.SaveModelConfigRequest request = request("MOCK", null);
        AiRepository.ModelConfig saved = config(2L, "DISABLED");
        when(repository.createConfig(admin, request, null)).thenReturn(saved);
        when(repository.toJson(org.mockito.ArgumentMatchers.any())).thenReturn("{}");

        AiAdminModels.ModelConfigResponse response = service.createConfig(admin, request);

        assertThat(response.status()).isEqualTo("DISABLED");
        verify(auditLogService).success(admin, "AI_MODEL_CONFIG_CREATE", "AI_MODEL_CONFIG", 2L, "{}");
    }

    @Test
    void enableConfigRecordsPreviousAndNewConfiguration() {
        AiRepository.ModelConfig previous = config(1L, "ENABLED");
        AiRepository.ModelConfig enabled = config(2L, "ENABLED");
        when(repository.requireActiveConfig()).thenReturn(previous);
        when(repository.enableConfig(2L, admin)).thenReturn(enabled);
        when(repository.toJson(org.mockito.ArgumentMatchers.any())).thenReturn("{}");

        AiAdminModels.ModelConfigResponse response = service.enableConfig(2L, admin);

        assertThat(response.id()).isEqualTo(2L);
        verify(repository).enableConfig(2L, admin);
        verify(auditLogService).success(admin, "AI_MODEL_CONFIG_ENABLE", "AI_MODEL_CONFIG", 2L, "{}");
    }

    private AiAdminModels.SaveModelConfigRequest request(String provider, Integer version) {
        return new AiAdminModels.SaveModelConfigRequest("配置", provider, "", null, "model", 30,
                true, true, true, true, "", "", 10, 30, 20, version);
    }

    private AiRepository.ModelConfig config(Long id, String status) {
        return new AiRepository.ModelConfig(id, "配置", "MOCK", "", null, "model", 30,
                true, true, true, true, "", "", 10, 30, 20, 1, status);
    }
}
