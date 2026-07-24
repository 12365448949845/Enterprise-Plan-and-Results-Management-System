package com.planning.platform.ai.controller;

import com.planning.platform.ai.service.AiReviewService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewControllerRoleTest {

    @Mock private AuthService authService;
    @Mock private PerformanceRoleGuard roleGuard;
    @Mock private AiReviewService aiReviewService;
    @Mock private Authentication authentication;
    @InjectMocks private AiReviewController controller;

    @Test
    void leaderCannotTriggerEnsureForEmployeePlan() {
        AuthUser leader = new AuthUser(30L, "leader", "直属领导", 110L, 110L,
                false, List.of("DIRECT_LEADER"), List.of());
        when(authService.requireAuthUser(authentication)).thenReturn(leader);
        doThrow(new BizException(403, "当前账号无员工端访问权限"))
                .when(roleGuard).requireEmployeeModule(leader);

        BizException error = catchThrowableOfType(
                () -> controller.ensurePlan(authentication, "MONTH_PLAN", 14L), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        verify(roleGuard).requireEmployeeModule(leader);
        verifyNoInteractions(aiReviewService);
    }
}
