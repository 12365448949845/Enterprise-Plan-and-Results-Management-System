package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportTaskCleanupService {

    private final BizExportTaskMapper exportTaskMapper;
    private final ExportFileService exportFileService;

    @Value("${planning.export.processing-timeout-minutes:30}")
    private long processingTimeoutMinutes;

    @Scheduled(cron = "${planning.export.cleanup-cron:0 20 3 * * *}")
    public void cleanupExpiredExports() {
        List<BizExportTask> expiredTasks = exportTaskMapper.selectList(new LambdaQueryWrapper<BizExportTask>()
                .eq(BizExportTask::getDeleted, 0)
                .eq(BizExportTask::getStatus, "SUCCESS")
                .isNotNull(BizExportTask::getExpireAt)
                .lt(BizExportTask::getExpireAt, LocalDateTime.now()));
        for (BizExportTask task : expiredTasks) {
            exportFileService.deleteTaskFiles(task);
            task.setStatus("EXPIRED");
            task.setIntegrityStatus("EXPIRED");
            task.setFilePath(null);
            task.setSizeText("0 B");
            task.setErrorMessage("导出文件已超过保留期限");
            exportTaskMapper.updateById(task);
        }
    }

    @Scheduled(
            fixedDelayString = "${planning.export.recovery-delay-ms:60000}",
            initialDelayString = "${planning.export.recovery-initial-delay-ms:60000}"
    )
    public void failStaleProcessingTasks() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(processingTimeoutMinutes);
        List<BizExportTask> staleTasks = exportTaskMapper.selectList(new LambdaQueryWrapper<BizExportTask>()
                .eq(BizExportTask::getDeleted, 0)
                .eq(BizExportTask::getStatus, "PROCESSING")
                .lt(BizExportTask::getUpdatedAt, deadline));
        for (BizExportTask task : staleTasks) {
            task.setStatus("FAILED");
            task.setIntegrityStatus("FAILED");
            task.setErrorMessage("导出任务执行超时，可重新发起");
            task.setFinishedAt(LocalDateTime.now());
            exportTaskMapper.updateById(task);
        }
    }
}
