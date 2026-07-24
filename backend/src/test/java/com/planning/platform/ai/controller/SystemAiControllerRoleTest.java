package com.planning.platform.ai.controller;

import com.planning.platform.ai.service.AiManagementService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAiControllerRoleTest {

    @Mock AuthService authService;
    @Mock AiManagementService managementService;
    @Mock Authentication authentication;
    @InjectMocks SystemAiController controller;

    @Test
    void employeeCannotReadModelConfiguration() {
        AuthUser employee = new AuthUser(10L, "employee", "员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        when(authService.requireAuthUser(authentication)).thenReturn(employee);

        BizException error = catchThrowableOfType(() -> controller.configs(authentication), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        verifyNoInteractions(managementService);
    }
}
