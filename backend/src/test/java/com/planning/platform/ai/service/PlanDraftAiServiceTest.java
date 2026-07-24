package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.model.PlanDraftAiModels;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizWeekPlan;
import com.planning.platform.planning.domain.BizWeekPlanItem;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizWeekPlanItemMapper;
import com.planning.platform.planning.mapper.BizWeekPlanMapper;
import com.planning.platform.system.service.WorkdayCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDraftAiServiceTest {

    @Mock AiRepository repository;
    @Mock AiInvocationService invocationService;
    @Mock AiRateLimitService rateLimitService;
    @Mock BizMonthPlanMapper monthPlanMapper;
    @Mock BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock BizWeekPlanMapper weekPlanMapper;
    @Mock BizWeekPlanItemMapper weekPlanItemMapper;
    @Mock BizDayPlanMapper dayPlanMapper;
    @Mock WorkdayCalendarService workdayCalendarService;
    @Mock PerformanceDataScopeService dataScopeService;

    private PlanDraftAiService service;
    private AuthUser employee;
    private AiRepository.ModelConfig config;

    @BeforeEach
    void setUp() {
        service = new PlanDraftAiService(repository, invocationService,
                new AiOutputValidator(new ObjectMapper().findAndRegisterModules()), rateLimitService,
                new ObjectMapper().findAndRegisterModules(), monthPlanMapper, monthPlanItemMapper,
                weekPlanMapper, weekPlanItemMapper, dayPlanMapper, workdayCalendarService, dataScopeService);
        employee = new AuthUser(7L, "employee", "员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        config = new AiRepository.ModelConfig(1L, "mock", "MOCK", "", "", "planning-mock-v1",
                30, true, true, true, true, "", "", 10, 30, 20, 1, "ENABLED");
        when(repository.activeConfig()).thenReturn(Optional.of(config));
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
    }

    @Test
    void weekContextExcludesAnotherEmployeesMonthItems() {
        BizMonthPlan owned = monthPlan(31L, 7L, "2026-07");
        BizMonthPlan another = monthPlan(32L, 8L, "2026-07");
        when(monthPlanMapper.selectList(any())).thenReturn(List.of(owned, another));
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(
                monthItem(101L, 31L, "完成接口"), monthItem(202L, 32L, "他人任务")));
        when(weekPlanMapper.selectList(any())).thenReturn(List.of());

        PlanDraftAiModels.ContextResponse response = service.weekContext(
                employee, LocalDate.parse("2026-07-27"));

        assertThat(response.parentOptions()).extracting(PlanDraftAiModels.ParentOption::id)
                .containsExactly(101L);
        assertThat(response.relatedWeekItems()).isEmpty();
    }

    @Test
    void dayContextIncludesMatchingWeekItemsAsReadOnlyContext() {
        BizMonthPlan owned = monthPlan(31L, 7L, "2026-07");
        BizMonthPlanItem parent = monthItem(101L, 31L, "完成接口");
        BizWeekPlan week = new BizWeekPlan();
        week.setId(41L);
        week.setOwnerUserId(7L);
        week.setWeekStart(LocalDate.parse("2026-07-27"));
        week.setWeekEnd(LocalDate.parse("2026-08-02"));
        week.setStatus("APPROVED");
        week.setDeleted(0);
        BizWeekPlanItem weekItem = new BizWeekPlanItem();
        weekItem.setId(51L);
        weekItem.setWeekPlanId(41L);
        weekItem.setMonthPlanItemId(101L);
        weekItem.setContent("完成接口开发与联调");
        weekItem.setDeliverable("联调记录");
        weekItem.setPlannedFinishDate(LocalDate.parse("2026-07-31"));
        weekItem.setDeleted(0);

        when(monthPlanMapper.selectList(any())).thenReturn(List.of(owned));
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(parent));
        when(weekPlanMapper.selectList(any())).thenReturn(List.of(week));
        when(weekPlanItemMapper.selectList(any())).thenReturn(List.of(weekItem));
        when(dayPlanMapper.selectList(any())).thenReturn(List.of());
        when(workdayCalendarService.resolve(LocalDate.parse("2026-07-30"))).thenReturn(
                new WorkdayCalendarService.CalendarDay(LocalDate.parse("2026-07-30"),
                        "WORKDAY", true, "工作日", null, null, false));

        PlanDraftAiModels.ContextResponse response = service.dayContext(
                employee, LocalDate.parse("2026-07-30"));

        assertThat(response.relatedWeekItems()).hasSize(1);
        assertThat(response.relatedWeekItems().get(0).monthPlanItemId()).isEqualTo(101L);
        assertThat(response.parentOptions()).extracting(PlanDraftAiModels.ParentOption::id)
                .containsExactly(101L);
    }

    private BizMonthPlan monthPlan(Long id, Long ownerId, String month) {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(id);
        plan.setOwnerUserId(ownerId);
        plan.setDeptId(110L);
        plan.setPlanMonth(month);
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        return plan;
    }

    private BizMonthPlanItem monthItem(Long id, Long planId, String name) {
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(id);
        item.setMonthPlanId(planId);
        item.setTaskName(name);
        item.setTaskContent(name + "内容");
        item.setDeliverable(name + "成果");
        item.setDeleted(0);
        return item;
    }
}
