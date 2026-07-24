package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ExportTaskWorker {

    private static final Set<String> DEPARTMENT_EXPORT_DIMENSIONS = Set.of(
            "DEPARTMENT_LEDGER", "MONTH_PLAN_APPROVAL_LIST", "RESULT_CONFIRM_LIST", "QUARTER_SUMMARY"
    );

    private final BizExportTaskMapper exportTaskMapper;
    private final ExportFileService exportFileService;
    private final BizTodoMapper todoMapper;

    @Async("exportTaskExecutor")
    public CompletableFuture<Void> generate(String taskId) {
        int claimed = exportTaskMapper.update(null, new UpdateWrapper<BizExportTask>()
                .eq("id", taskId)
                .eq("deleted", 0)
                .eq("status", "PENDING")
                .set("status", "PROCESSING")
                .set("error_message", null));
        if (claimed != 1) {
            return CompletableFuture.completedFuture(null);
        }
        BizExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            return CompletableFuture.completedFuture(null);
        }
        task.setStatus("PROCESSING");
        try {
            exportFileService.generate(task);
        } catch (RuntimeException ex) {
            task.setStatus("FAILED");
            task.setIntegrityStatus("FAILED");
            task.setErrorMessage(ex.getMessage() == null ? "导出文件生成失败" : ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
        }
        exportTaskMapper.updateById(task);
        upsertCompletionTodo(task);
        return CompletableFuture.completedFuture(null);
    }

    private void upsertCompletionTodo(BizExportTask task) {
        if (task.getDimensionType() == null
                || !DEPARTMENT_EXPORT_DIMENSIONS.contains(task.getDimensionType())
                || task.getRequestedBy() == null) {
            return;
        }
        BizTodo todo = todoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, task.getRequestedBy())
                .eq(BizTodo::getObjectType, "EXPORT_TASK")
                .eq(BizTodo::getObjectId, task.getId())
                .last("LIMIT 1"));
        if (todo == null) {
            todo = new BizTodo();
            todo.setSceneCode("EXPORT_DONE");
            todo.setReceiverId(task.getRequestedBy());
            todo.setReceiverName(task.getRequestedByName());
            todo.setObjectType("EXPORT_TASK");
            todo.setObjectId(task.getId());
            todo.setMessageType("NOTICE");
            todo.setRemindCount(0);
            todo.setRouteHint("/department/export-tasks");
            todo.setDeptId(task.getDeptId());
            todo.setCreatedBy(task.getRequestedBy());
            todo.setDeleted(0);
        }
        boolean success = "SUCCESS".equals(task.getStatus());
        todo.setTitle(success ? "资料包导出完成" : "资料包导出失败");
        todo.setTriggerText(success ? "导出文件已生成并完成完整性校验" : "导出任务生成失败，可查看原因后重试");
        todo.setDueAt(success && task.getExpireAt() != null ? task.getExpireAt() : LocalDateTime.now().plusDays(1));
        todo.setRequirementText(success ? "下载并核对导出资料包" : "查看失败原因并重新发起导出");
        todo.setImpactText(success ? "文件过期后需要重新生成" : "影响部门台账资料交付");
        todo.setStatus("UNREAD");
        todo.setUpdatedBy(task.getRequestedBy());
        if (todo.getId() == null) {
            todoMapper.insert(todo);
        } else {
            todoMapper.updateById(todo);
        }
    }
}
