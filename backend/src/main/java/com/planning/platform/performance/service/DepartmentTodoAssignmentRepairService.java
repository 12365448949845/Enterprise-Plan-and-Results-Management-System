package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(4)
@RequiredArgsConstructor
public class DepartmentTodoAssignmentRepairService implements CommandLineRunner {

    private final BizMonthPlanMapper monthPlanMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizTodoMapper todoMapper;
    private final PerformanceDataScopeService dataScopeService;

    @Override
    public void run(String... args) {
        repairMonthPlanTodos();
        repairDayPlanReviewTodos();
        repairResultConfirmTodos();
    }

    private void repairMonthPlanTodos() {
        List<BizMonthPlan> plans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getStatus, "PENDING"));
        for (BizMonthPlan plan : plans) {
            Long receiverId = dataScopeService.directLeaderId(plan.getOwnerUserId());
            if (receiverId == null) {
                continue;
            }
            if (!receiverId.equals(plan.getApproverId())) {
                plan.setApproverId(receiverId);
                plan.setUpdatedBy(receiverId);
                monthPlanMapper.updateById(plan);
            }
            SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
            SysUser receiver = dataScopeService.requireUser(receiverId);
            repairTodo("MONTH_PLAN_APPROVAL", "月计划待审批", owner.getRealName() + "提交了月计划",
                    "MONTH_PLAN", String.valueOf(plan.getId()),
                    plan.getSubmitAt() == null ? LocalDateTime.now().plusDays(2) : plan.getSubmitAt().plusDays(2),
                    "审批通过或驳回月计划", "影响月度目标生效", "/leader/month-plan-approval",
                    plan.getDeptId(), plan.getCreatedBy(), receiver);
        }
    }

    private void repairResultConfirmTodos() {
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getStatus, "PENDING")
                .in(BizResult::getSuggestionStatus, List.of("SUGGEST_CONFIRM", "SUGGEST_REJECT")));
        for (BizResult result : results) {
            Long receiverId = dataScopeService.departmentOwnerId(result.getDeptId());
            if (receiverId == null) {
                continue;
            }
            SysUser owner = dataScopeService.requireUser(result.getOwnerUserId());
            SysUser receiver = dataScopeService.requireUser(receiverId);
            repairTodo("RESULT_CONFIRM", "成果最终确认", owner.getRealName() + "的成果已提交确认建议",
                    "RESULT", String.valueOf(result.getId()),
                    result.getSuggestedAt() == null ? LocalDateTime.now().plusDays(1) : result.getSuggestedAt().plusDays(1),
                    "完成最终确认或驳回", "影响成果闭环率", "/department/result-confirm",
                    result.getDeptId(), result.getCreatedBy(), receiver);
        }
    }

    private void repairDayPlanReviewTodos() {
        List<BizDayPlan> plans = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getStatus, "PENDING")
                .eq(BizDayPlan::getReviewStatus, "RISK_MARKED"));
        for (BizDayPlan plan : plans) {
            Long receiverId = dataScopeService.departmentOwnerId(plan.getDeptId());
            if (receiverId == null) {
                continue;
            }
            SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
            SysUser receiver = dataScopeService.requireUser(receiverId);
            repairTodo("DAY_PLAN_REVIEW", "日计划补审", owner.getRealName() + "的日计划已标记风险",
                    "DAY_PLAN", String.valueOf(plan.getId()),
                    plan.getApprovalDueAt() == null ? LocalDateTime.now().plusDays(1) : plan.getApprovalDueAt(),
                    "复核风险并通过或退回补充", "影响日计划闭环率", "/department/todo",
                    plan.getDeptId(), plan.getCreatedBy(), receiver);
        }
    }

    private void repairTodo(String sceneCode, String title, String triggerText, String objectType, String objectId,
                            LocalDateTime dueAt, String requirement, String impact, String routeHint, Long deptId,
                            Long createdBy, SysUser receiver) {
        List<BizTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId)
                .ne(BizTodo::getStatus, "DONE")
                .orderByAsc(BizTodo::getId));
        BizTodo todo = todos.isEmpty() ? new BizTodo() : todos.get(0);
        if (todo.getId() == null) {
            todo.setStatus("UNREAD");
            todo.setRemindCount(0);
            todo.setCreatedBy(createdBy);
            todo.setDeleted(0);
        }
        todo.setSceneCode(sceneCode);
        todo.setTitle(title);
        todo.setTriggerText(triggerText);
        todo.setObjectType(objectType);
        todo.setObjectId(objectId);
        todo.setDueAt(dueAt);
        todo.setRequirementText(requirement);
        todo.setImpactText(impact);
        todo.setRouteHint(routeHint);
        todo.setDeptId(deptId);
        todo.setReceiverId(receiver.getId());
        todo.setReceiverName(receiver.getRealName());
        todo.setUpdatedBy(receiver.getId());
        if (todo.getId() == null) {
            todoMapper.insert(todo);
        } else {
            todoMapper.updateById(todo);
        }
        for (int index = 1; index < todos.size(); index++) {
            BizTodo duplicate = todos.get(index);
            duplicate.setStatus("DONE");
            duplicate.setUpdatedBy(receiver.getId());
            todoMapper.updateById(duplicate);
        }
    }
}
