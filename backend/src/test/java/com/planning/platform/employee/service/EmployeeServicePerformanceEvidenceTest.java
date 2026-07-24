package com.planning.platform.employee.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.service.ExportFileService;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.performance.service.PerformanceJsonCodec;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.service.PlanningAccessService;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import com.planning.platform.system.service.AuditLogService;
import com.planning.platform.system.service.WorkdayCalendarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServicePerformanceEvidenceTest {

    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock
    private BizDayPlanMapper dayPlanMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper resultEvidenceMapper;
    @Mock
    private BizEmployeeAppealMapper appealMapper;
    @Mock
    private SysAuditLogMapper auditLogMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlanningAccessService accessService;
    @Mock
    private PerformanceDataScopeService dataScopeService;
    @Mock
    private BizTodoMapper todoMapper;
    @Mock
    private BizPlanAdjustmentMapper planAdjustmentMapper;
    @Mock
    private BizExportTaskMapper exportTaskMapper;
    @Mock
    private ExportFileService exportFileService;
    @Mock
    private PerformanceJsonCodec jsonCodec;
    @Mock
    private WorkdayCalendarService workdayCalendarService;
    @Mock
    private UserMessageService messageService;

    @InjectMocks
    private EmployeeService employeeService;

    private final AuthUser employee = new AuthUser(10L, "employee", "演示员工", 110L, 20L,
            false, List.of("EMPLOYEE"), List.of());

    @Test
    void dashboardUsesDatabaseOrganizationAndOpenAppealCount() {
        when(monthPlanMapper.selectList(any())).thenReturn(List.of());
        when(dayPlanMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(appealMapper.selectCount(any())).thenReturn(2L);
        when(dataScopeService.departmentName(employee.deptId())).thenReturn("产品一组");

        Map<String, Object> response = employeeService.dashboard(employee, LocalDate.now().toString().substring(0, 7));

        assertThat(response.get("orgName")).isEqualTo("产品一组");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) response.get("summary");
        assertThat(summary.get("openAppealCount")).isEqualTo(2L);
    }

    @Test
    void aggregatesCurrentMonthEvidenceFromAllBusinessSources() {
        LocalDate today = LocalDate.now();
        String month = today.toString().substring(0, 7);

        BizMonthPlan monthPlan = new BizMonthPlan();
        monthPlan.setId(1L);
        monthPlan.setPlanMonth(month);
        monthPlan.setTitle("本月重点工作");
        monthPlan.setContent("推进重点任务");
        monthPlan.setStatus("APPROVED");
        monthPlan.setCreatedAt(today.atTime(8, 0));

        BizDayPlan dayPlan = new BizDayPlan();
        dayPlan.setId(2L);
        dayPlan.setPlanDate(today);
        dayPlan.setTitle("今日计划");
        dayPlan.setContent("完成接口联调");
        dayPlan.setStatus("APPROVED");
        dayPlan.setReviewStatus("COMMENTED");
        dayPlan.setMonthPlanItemId(21L);
        dayPlan.setCreatedAt(today.atTime(8, 30));

        BizMonthPlanItem linkedItem = new BizMonthPlanItem();
        linkedItem.setId(21L);
        linkedItem.setCompletionRate(45);
        linkedItem.setDeleted(0);

        BizResult result = new BizResult();
        result.setId(3L);
        result.setResultDate(today);
        result.setTitle("联调成果");
        result.setContent("接口联调完成");
        result.setCompletionRate(86);
        result.setEvidenceStatus("COMPLETE");
        result.setStatus("CONFIRMED");
        result.setCreatedAt(today.atTime(9, 0));
        result.setDeleted(0);

        BizEmployeeAppeal appeal = new BizEmployeeAppeal();
        appeal.setId(4L);
        appeal.setTitle("成果确认申诉");
        appeal.setReason("确认意见需要复核");
        appeal.setStatus("SUBMITTED");
        appeal.setRelatedResultId(3L);
        appeal.setCreatedAt(today.atTime(10, 0));

        when(monthPlanMapper.selectList(any())).thenReturn(List.of(monthPlan));
        when(dayPlanMapper.selectList(any())).thenReturn(List.of(dayPlan));
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        when(appealMapper.selectList(any())).thenReturn(List.of(appeal));
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(resultMapper.selectById(3L)).thenReturn(result);
        when(monthPlanItemMapper.selectById(21L)).thenReturn(linkedItem);

        Map<String, Object> response = employeeService.performanceEvidence(employee, "month");

        assertThat(response.get("periodType")).isEqualTo("month");
        assertThat(response.get("periodStart")).isEqualTo(today.withDayOfMonth(1));
        assertThat(response.get("periodEnd")).isEqualTo(today.withDayOfMonth(1).plusMonths(1).minusDays(1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertThat(items).hasSize(4);
        assertThat(items).extracting(item -> item.get("sourceType"))
                .containsExactlyInAnyOrder("month_plan", "day_plan", "result", "appeal");
        assertThat(scoreOf(items, "month_plan")).isEqualTo(86);
        assertThat(scoreOf(items, "day_plan")).isEqualTo(45);
        assertThat(scoreOf(items, "result")).isEqualTo(86);
        assertThat(scoreOf(items, "appeal")).isEqualTo(86);
    }

    @Test
    void rejectsUnsupportedPeriodType() {
        assertThatThrownBy(() -> employeeService.performanceEvidence(employee, "half-year"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("统计周期仅支持");
    }

    private int scoreOf(List<Map<String, Object>> items, String sourceType) {
        return items.stream()
                .filter(item -> sourceType.equals(item.get("sourceType")))
                .map(item -> (Integer) item.get("score"))
                .findFirst()
                .orElseThrow();
    }
}
