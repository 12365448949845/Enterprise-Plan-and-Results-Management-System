package com.planning.platform.planning.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningAccessServiceTest {

    @Mock
    private PerformanceDataScopeService dataScopeService;

    @InjectMocks
    private PlanningAccessService accessService;

    @Test
    void projectManagerCanOnlyAccessAuthorizedOwners() {
        AuthUser manager = user(20L, "PROJECT_MANAGER");
        when(dataScopeService.leaderOwnerIds(manager, null)).thenReturn(Set.of(10L));

        assertThat(accessService.accessibleOwnerIds(manager)).containsExactlyInAnyOrder(20L, 10L);

        BizException error = catchThrowableOfType(
                () -> accessService.requireOwnerOrManager(manager, 99L),
                BizException.class
        );
        assertThat(error.getCode()).isEqualTo(403);
    }

    @Test
    void departmentOwnerUsesDepartmentDataScope() {
        AuthUser owner = user(30L, "DEPT_OWNER");
        when(dataScopeService.departmentOwnerIds(owner, null)).thenReturn(Set.of(10L, 11L));

        accessService.requireManage(owner, 11L);

        assertThat(accessService.accessibleOwnerIds(owner)).contains(30L, 10L, 11L);
    }

    @Test
    void employeeEndpointOwnershipCannotBeElevatedByManagerRole() {
        AuthUser manager = user(20L, "PROJECT_MANAGER");

        BizException error = catchThrowableOfType(
                () -> accessService.requireOwner(manager, 10L),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
    }

    private AuthUser user(Long id, String role) {
        return new AuthUser(id, "user" + id, "User " + id, 110L, 110L,
                false, List.of(role), List.of());
    }
}
