package com.planning.platform.ai.controller;

import com.planning.platform.ai.service.MonthPlanAiService;
import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAiControllerRoleTest {

    @Mock AuthService authService;
    @Mock PerformanceRoleGuard roleGuard;
    @Mock MonthPlanAiService aiService;
    @Mock Authentication authentication;
    @InjectMocks EmployeeAiController controller;

    @Test
    void leaderCannotReadEmployeeAiContext() {
        AuthUser leader = new AuthUser(20L, "leader", "直属领导", 110L, 110L,
                false, List.of("DIRECT_LEADER"), List.of());
        when(authService.requireAuthUser(authentication)).thenReturn(leader);
        doThrow(new BizException(403, "当前账号无员工端访问权限"))
                .when(roleGuard).requireEmployeeModule(leader);

        BizException error = catchThrowableOfType(
                () -> controller.context(authentication, YearMonth.now().toString()), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        verify(roleGuard).requireEmployeeModule(leader);
        verifyNoInteractions(aiService);
    }
}
