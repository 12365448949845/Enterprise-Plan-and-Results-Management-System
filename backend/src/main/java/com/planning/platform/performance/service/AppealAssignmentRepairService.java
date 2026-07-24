package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.system.domain.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
public class AppealAssignmentRepairService implements CommandLineRunner {

    private final BizEmployeeAppealMapper appealMapper;
    private final BizTodoMapper todoMapper;
    private final PerformanceDataScopeService dataScopeService;

    @Override
    public void run(String... args) {
        List<BizEmployeeAppeal> appeals = appealMapper.selectList(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING")));
        for (BizEmployeeAppeal appeal : appeals) {
            Long handlerId = dataScopeService.departmentOwnerId(appeal.getDeptId());
            if (handlerId == null) {
                continue;
            }
            if (!handlerId.equals(appeal.getHandlerId())) {
                appeal.setHandlerId(handlerId);
                appeal.setUpdatedBy(handlerId);
                appealMapper.updateById(appeal);
            }
            repairTodo(appeal, dataScopeService.requireUser(handlerId));
        }
    }

    private void repairTodo(BizEmployeeAppeal appeal, SysUser handler) {
        BizTodo todo = todoMapper.selectOne(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, "APPEAL")
                .eq(BizTodo::getObjectId, String.valueOf(appeal.getId()))
                .ne(BizTodo::getStatus, "DONE")
                .last("LIMIT 1"));
        if (todo == null) {
            todo = new BizTodo();
            todo.setSceneCode("APPEAL_PROCESS");
            todo.setTitle("申诉待处理");
            todo.setTriggerText("员工提交了绩效申诉");
            todo.setObjectType("APPEAL");
            todo.setObjectId(String.valueOf(appeal.getId()));
            todo.setDueAt(LocalDateTime.now().plusDays(3));
            todo.setRequirementText("查看申诉依据并完成处理");
            todo.setImpactText("影响员工绩效闭环");
            todo.setStatus("UNREAD");
            todo.setRemindCount(0);
            todo.setRouteHint("/department/todo");
            todo.setDeptId(appeal.getDeptId());
            todo.setCreatedBy(appeal.getCreatedBy());
            todo.setDeleted(0);
        }
        todo.setReceiverId(handler.getId());
        todo.setReceiverName(handler.getRealName());
        todo.setUpdatedBy(handler.getId());
        if (todo.getId() == null) {
            todoMapper.insert(todo);
        } else {
            todoMapper.updateById(todo);
        }
    }
}
