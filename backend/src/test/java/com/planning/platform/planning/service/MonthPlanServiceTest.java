package com.planning.platform.planning.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.dto.MonthPlanSaveReqDTO;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonthPlanServiceTest {

    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private PlanningAccessService accessService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private MonthPlanService monthPlanService;

    @Test
    void invalidCalendarMonthCannotBePersisted() {
        AuthUser employee = new AuthUser(10L, "employee", "Employee", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        MonthPlanSaveReqDTO request = new MonthPlanSaveReqDTO();
        request.setTitle("Invalid month");
        request.setPlanMonth("2026-99");
        request.setContent("Should not persist");

        BizException error = catchThrowableOfType(
                () -> monthPlanService.create(employee, request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        verify(monthPlanMapper, never()).insert(any(BizMonthPlan.class));
    }
}
