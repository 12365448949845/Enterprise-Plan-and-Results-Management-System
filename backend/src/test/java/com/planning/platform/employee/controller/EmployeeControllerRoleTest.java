package com.planning.platform.employee.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.employee.service.EmployeeAppealPackageService;
import com.planning.platform.employee.service.EmployeeService;
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
class EmployeeControllerRoleTest {

    @Mock
    private AuthService authService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private EmployeeAppealPackageService employeeAppealPackageService;
    @Mock
    private PerformanceRoleGuard roleGuard;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private EmployeeController controller;

    @Test
    void leaderCannotCallEmployeeDashboard() {
        AuthUser leader = new AuthUser(20L, "leader", "直属领导", 110L, 110L,
                false, List.of("DIRECT_LEADER"), List.of());
        when(authService.requireAuthUser(authentication)).thenReturn(leader);
        doThrow(new BizException(403, "当前账号无员工端访问权限"))
                .when(roleGuard).requireEmployeeModule(leader);

        BizException error = catchThrowableOfType(
                () -> controller.dashboard(authentication, "2026-07"),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        verify(roleGuard).requireEmployeeModule(leader);
        verifyNoInteractions(employeeService);
    }

    @Test
    void employeeRolePassesGuardBeforeBusinessCall() {
        AuthUser employee = new AuthUser(10L, "employee", "员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        when(authService.requireAuthUser(authentication)).thenReturn(employee);

        controller.dashboard(authentication, "2026-07");

        verify(roleGuard).requireEmployeeModule(employee);
        verify(employeeService).dashboard(employee, "2026-07");
    }
}
