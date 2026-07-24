package com.planning.platform.performance.service;

import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentTodoAssignmentRepairServiceTest {

    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizDayPlanMapper dayPlanMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizTodoMapper todoMapper;
    @Mock
    private PerformanceDataScopeService dataScopeService;

    @InjectMocks
    private DepartmentTodoAssignmentRepairService repairService;

    @Test
    void monthPlanTodoMovesToDirectLeaderWhileDepartmentWorkflowsStayWithDepartmentOwner() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(10L);
        plan.setOwnerUserId(4L);
        plan.setDeptId(110L);
        plan.setStatus("PENDING");
        plan.setDeleted(0);
        BizResult result = new BizResult();
        result.setId(11L);
        result.setOwnerUserId(4L);
        result.setDeptId(110L);
        result.setStatus("PENDING");
        result.setSuggestionStatus("SUGGEST_CONFIRM");
        result.setDeleted(0);
        BizDayPlan dayPlan = new BizDayPlan();
        dayPlan.setId(12L);
        dayPlan.setOwnerUserId(4L);
        dayPlan.setDeptId(110L);
        dayPlan.setStatus("PENDING");
        dayPlan.setReviewStatus("RISK_MARKED");
        dayPlan.setDeleted(0);
        SysUser employee = new SysUser();
        employee.setId(4L);
        employee.setRealName("员工");
        SysUser departmentOwner = new SysUser();
        departmentOwner.setId(2L);
        departmentOwner.setRealName("部门负责人");
        SysUser directLeader = new SysUser();
        directLeader.setId(3L);
        directLeader.setRealName("直属领导");
        BizTodo monthTodo = todo(20L, "MONTH_PLAN", "10");
        BizTodo resultTodo = todo(21L, "RESULT", "11");
        BizTodo dayTodo = todo(22L, "DAY_PLAN", "12");
        when(monthPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(dayPlanMapper.selectList(any())).thenReturn(List.of(dayPlan));
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        when(dataScopeService.departmentOwnerId(110L)).thenReturn(departmentOwner.getId());
        when(dataScopeService.directLeaderId(employee.getId())).thenReturn(directLeader.getId());
        when(dataScopeService.requireUser(employee.getId())).thenReturn(employee);
        when(dataScopeService.requireUser(departmentOwner.getId())).thenReturn(departmentOwner);
        when(dataScopeService.requireUser(directLeader.getId())).thenReturn(directLeader);
        when(todoMapper.selectList(any())).thenReturn(List.of(monthTodo), List.of(dayTodo), List.of(resultTodo));

        repairService.run();

        assertThat(monthTodo.getReceiverId()).isEqualTo(directLeader.getId());
        assertThat(monthTodo.getRouteHint()).isEqualTo("/leader/month-plan-approval");
        assertThat(plan.getApproverId()).isEqualTo(directLeader.getId());
        assertThat(dayTodo.getReceiverId()).isEqualTo(departmentOwner.getId());
        assertThat(dayTodo.getTitle()).isEqualTo("日计划补审");
        assertThat(dayTodo.getRouteHint()).isEqualTo("/department/todo");
        assertThat(resultTodo.getReceiverId()).isEqualTo(departmentOwner.getId());
        verify(todoMapper).updateById(monthTodo);
        verify(todoMapper).updateById(dayTodo);
        verify(todoMapper).updateById(resultTodo);
        verify(monthPlanMapper).updateById(plan);
    }

    private BizTodo todo(Long id, String objectType, String objectId) {
        BizTodo todo = new BizTodo();
        todo.setId(id);
        todo.setObjectType(objectType);
        todo.setObjectId(objectId);
        todo.setReceiverId(3L);
        todo.setReceiverName("直属领导");
        todo.setStatus("UNREAD");
        todo.setDeleted(0);
        return todo;
    }
}
