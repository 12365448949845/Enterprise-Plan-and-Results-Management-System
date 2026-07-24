package com.planning.platform.planning.service;

import com.planning.platform.ai.service.AiReviewService;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizWeekPlan;
import com.planning.platform.planning.domain.BizWeekPlanItem;
import com.planning.platform.planning.dto.WeekPlanDecisionReqDTO;
import com.planning.platform.planning.dto.WeekPlanSaveReqDTO;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizWeekPlanItemMapper;
import com.planning.platform.planning.mapper.BizWeekPlanMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeekPlanServiceTest {

    @Mock private BizWeekPlanMapper weekPlanMapper;
    @Mock private BizWeekPlanItemMapper weekPlanItemMapper;
    @Mock private BizMonthPlanMapper monthPlanMapper;
    @Mock private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock private BizTodoMapper todoMapper;
    @Mock private PerformanceRoleGuard roleGuard;
    @Mock private PerformanceDataScopeService dataScopeService;
    @Mock private AuditLogService auditLogService;
    @Mock private UserMessageService messageService;
    @Mock private AiReviewService aiReviewService;
    @InjectMocks private WeekPlanService service;

    private final AuthUser employee = new AuthUser(10L, "employee", "员工甲", 110L, null,
            false, List.of("EMPLOYEE"), List.of());
    private final AuthUser leader = new AuthUser(20L, "leader", "直属领导", 110L, null,
            false, List.of("DIRECT_LEADER"), List.of());

    @Test
    void createDraftRejectsNonMondayBeforeWritingData() {
        WeekPlanSaveReqDTO request = request(LocalDate.of(2026, 7, 21), 101L);

        BizException error = catchThrowableOfType(() -> service.createDraft(employee, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("周一");
        verifyNoInteractions(weekPlanMapper, weekPlanItemMapper);
    }

    @Test
    void createDraftRejectsPastNaturalWeekBeforeWritingData() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate currentMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        WeekPlanSaveReqDTO request = request(currentMonday.minusWeeks(1), 101L);

        BizException error = catchThrowableOfType(() -> service.createDraft(employee, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("当前自然周及以后");
        verifyNoInteractions(weekPlanMapper, weekPlanItemMapper);
    }

    @Test
    void createDraftRejectsParentOwnedByAnotherEmployee() {
        LocalDate monday = LocalDate.of(2099, 7, 20);
        when(weekPlanMapper.selectCount(any())).thenReturn(0L);
        BizMonthPlanItem parentItem = parentItem(101L, 201L, "REGULAR", "APPROVED");
        BizMonthPlan parentPlan = parentPlan(201L, 99L, "APPROVED");
        when(monthPlanItemMapper.selectById(101L)).thenReturn(parentItem);
        when(monthPlanMapper.selectById(201L)).thenReturn(parentPlan);

        BizException error = catchThrowableOfType(() -> service.createDraft(employee, request(monday, 101L)), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        assertThat(error.getMessage()).contains("其他员工");
        verifyNoInteractions(weekPlanItemMapper);
    }

    @Test
    void createDraftRejectsUnapprovedExtraParent() {
        LocalDate monday = LocalDate.of(2099, 7, 20);
        when(weekPlanMapper.selectCount(any())).thenReturn(0L);
        when(monthPlanItemMapper.selectById(101L)).thenReturn(parentItem(101L, 201L, "EXTRA", "PENDING"));
        when(monthPlanMapper.selectById(201L)).thenReturn(parentPlan(201L, 10L, "APPROVED"));

        BizException error = catchThrowableOfType(() -> service.createDraft(employee, request(monday, 101L)), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("额外月计划条目审批通过");
    }

    @Test
    void createDraftRejectsParentMonthWithoutWeekOverlap() {
        LocalDate monday = LocalDate.of(2099, 7, 20);
        when(weekPlanMapper.selectCount(any())).thenReturn(0L);
        when(monthPlanItemMapper.selectById(101L)).thenReturn(parentItem(101L, 201L, "REGULAR", "APPROVED"));
        BizMonthPlan august = parentPlan(201L, 10L, "APPROVED");
        august.setPlanMonth("2099-08");
        when(monthPlanMapper.selectById(201L)).thenReturn(august);

        BizException error = catchThrowableOfType(() -> service.createDraft(employee, request(monday, 101L)), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("没有交集");
    }

    @Test
    void submitCreatesDirectLeaderTodoAndMovesToPending() {
        LocalDate monday = LocalDate.of(2099, 7, 20);
        BizWeekPlan plan = weekPlan(301L, "DRAFT", 2, monday);
        BizWeekPlanItem item = new BizWeekPlanItem();
        item.setWeekPlanId(301L);
        item.setMonthPlanItemId(101L);
        item.setContent("完成接口联调");
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(plan);
        when(weekPlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(monthPlanItemMapper.selectById(101L)).thenReturn(parentItem(101L, 201L, "REGULAR", "APPROVED"));
        when(monthPlanMapper.selectById(201L)).thenReturn(parentPlan(201L, 10L, "APPROVED"));
        when(dataScopeService.directLeaderId(10L)).thenReturn(20L);
        when(todoMapper.selectCount(any())).thenReturn(0L);
        when(dataScopeService.requireUser(20L)).thenReturn(user(20L, "直属领导"));

        var result = service.submit(employee, 301L, 2);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.versionNo()).isEqualTo(3);
        assertThat(plan.getApproverId()).isEqualTo(20L);
        ArgumentCaptor<BizTodo> todo = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(todo.capture());
        assertThat(todo.getValue().getSceneCode()).isEqualTo("WEEK_PLAN_APPROVAL");
        assertThat(todo.getValue().getReceiverId()).isEqualTo(20L);
    }

    @Test
    void approveRequiresExactDirectLeaderAndRecordsResult() {
        LocalDate monday = LocalDate.of(2099, 7, 20);
        BizWeekPlan plan = weekPlan(301L, "PENDING", 3, monday);
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(plan);
        when(dataScopeService.directLeaderId(10L)).thenReturn(20L);
        when(todoMapper.selectList(any())).thenReturn(List.of());
        when(dataScopeService.requireUser(10L)).thenReturn(user(10L, "员工甲"));
        WeekPlanDecisionReqDTO request = new WeekPlanDecisionReqDTO();
        request.setVersionNo(3);
        request.setComment("同意执行");

        var result = service.approve(leader, 301L, request);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.versionNo()).isEqualTo(4);
        assertThat(plan.getApprovalComment()).isEqualTo("同意执行");
        verify(weekPlanMapper).updateById(plan);
        ArgumentCaptor<BizTodo> notification = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(notification.capture());
        assertThat(notification.getValue().getSceneCode()).isEqualTo("WEEK_PLAN_APPROVAL_RESULT");
    }

    @Test
    void approveRejectsNonDirectLeader() {
        AuthUser otherLeader = new AuthUser(21L, "other", "其他领导", 110L, null,
                false, List.of("DIRECT_LEADER"), List.of());
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(weekPlan(301L, "PENDING", 3, LocalDate.of(2099, 7, 20)));
        when(dataScopeService.directLeaderId(10L)).thenReturn(20L);
        WeekPlanDecisionReqDTO request = new WeekPlanDecisionReqDTO();
        request.setVersionNo(3);

        BizException error = catchThrowableOfType(() -> service.approve(otherLeader, 301L, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        assertThat(error.getMessage()).contains("直属领导");
    }

    @Test
    void approveRejectsStaleVersion() {
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(weekPlan(301L, "PENDING", 4, LocalDate.of(2099, 7, 20)));
        when(dataScopeService.directLeaderId(10L)).thenReturn(20L);
        WeekPlanDecisionReqDTO request = new WeekPlanDecisionReqDTO();
        request.setVersionNo(3);

        BizException error = catchThrowableOfType(() -> service.approve(leader, 301L, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("刷新后重试");
    }

    @Test
    void withdrawMovesPendingPlanBackToDraft() {
        BizWeekPlan plan = weekPlan(301L, "PENDING", 3, LocalDate.of(2099, 7, 20));
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of());

        var result = service.withdraw(employee, 301L, 3);

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.versionNo()).isEqualTo(4);
        assertThat(plan.getApproverId()).isNull();
        verify(weekPlanMapper).updateById(plan);
    }

    @Test
    void deleteOnlyAllowsDraftOrRejectedPlan() {
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(weekPlan(301L, "APPROVED", 4, LocalDate.of(2099, 7, 20)));

        BizException error = catchThrowableOfType(() -> service.deleteDraft(employee, 301L, 4), BizException.class);

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("草稿或已驳回");
    }

    @Test
    void deleteDraftClearsItemsAndKeepsAuditTrail() {
        BizWeekPlan plan = weekPlan(301L, "DRAFT", 2, LocalDate.of(2099, 7, 20));
        when(weekPlanMapper.selectForUpdateById(301L)).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of());

        var result = service.deleteDraft(employee, 301L, 2);

        assertThat(result.message()).contains("已删除");
        assertThat(plan.getVersionNo()).isEqualTo(3);
        verify(weekPlanItemMapper).delete(any());
        verify(weekPlanMapper).deleteById(301L);
    }

    @Test
    void rejectRequiresReason() {
        WeekPlanDecisionReqDTO request = new WeekPlanDecisionReqDTO();
        request.setVersionNo(1);
        request.setComment("  ");

        BizException error = catchThrowableOfType(() -> service.reject(leader, 301L, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("驳回原因");
        verifyNoInteractions(weekPlanMapper);
    }

    @Test
    void departmentCannotReadPendingPlan() {
        AuthUser departmentOwner = new AuthUser(30L, "owner", "部门负责人", 110L, null,
                false, List.of("DEPT_OWNER"), List.of());
        when(weekPlanMapper.selectById(301L)).thenReturn(weekPlan(301L, "PENDING", 3, LocalDate.of(2099, 7, 20)));

        BizException error = catchThrowableOfType(() -> service.departmentDetail(departmentOwner, 301L), BizException.class);

        assertThat(error.getCode()).isEqualTo(404);
        assertThat(error.getMessage()).contains("审批结果不存在");
    }

    private WeekPlanSaveReqDTO request(LocalDate weekStart, Long parentId) {
        WeekPlanSaveReqDTO.Item item = new WeekPlanSaveReqDTO.Item();
        item.setMonthPlanItemId(parentId);
        item.setContent("本周完成核心功能");
        WeekPlanSaveReqDTO request = new WeekPlanSaveReqDTO();
        request.setWeekStart(weekStart);
        request.setItems(List.of(item));
        return request;
    }

    private BizMonthPlanItem parentItem(Long id, Long planId, String type, String status) {
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(id);
        item.setMonthPlanId(planId);
        item.setTaskType(type);
        item.setTaskName("月计划任务");
        item.setStatus(status);
        item.setDeleted(0);
        return item;
    }

    private BizMonthPlan parentPlan(Long id, Long ownerId, String status) {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(id);
        plan.setOwnerUserId(ownerId);
        plan.setDeptId(110L);
        plan.setPlanMonth("2099-07");
        plan.setTitle("2099年7月计划");
        plan.setStatus(status);
        plan.setDeleted(0);
        return plan;
    }

    private BizWeekPlan weekPlan(Long id, String status, int version, LocalDate monday) {
        BizWeekPlan plan = new BizWeekPlan();
        plan.setId(id);
        plan.setTitle("周计划");
        plan.setWeekStart(monday);
        plan.setWeekEnd(monday.plusDays(6));
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setStatus(status);
        plan.setVersionNo(version);
        plan.setDeleted(0);
        return plan;
    }

    private SysUser user(Long id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(name);
        user.setDeleted(0);
        return user;
    }
}
