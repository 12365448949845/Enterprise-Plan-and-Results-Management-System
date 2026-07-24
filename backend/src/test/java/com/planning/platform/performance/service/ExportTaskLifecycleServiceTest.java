package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ExportTaskLifecycleServiceTest {

    @Mock
    private BizExportTaskMapper exportTaskMapper;
    @Mock
    private ExportFileService exportFileService;
    @Mock
    private BizTodoMapper todoMapper;

    @InjectMocks
    private ExportTaskWorker worker;
    @InjectMocks
    private ExportTaskCleanupService cleanupService;

    @Test
    void workerClaimsPendingTaskAndPersistsSuccessfulGeneration() {
        BizExportTask task = task("EXP-1", "PROCESSING");
        task.setDimensionType("DEPARTMENT_LEDGER");
        task.setRequestedBy(30L);
        task.setRequestedByName("部门负责人");
        task.setDeptId(100L);
        when(exportTaskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        doAnswer(invocation -> {
            BizExportTask value = invocation.getArgument(0);
            value.setStatus("SUCCESS");
            value.setIntegrityStatus("COMPLETE");
            value.setFinishedAt(LocalDateTime.now());
            return null;
        }).when(exportFileService).generate(task);

        worker.generate(task.getId()).join();

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getIntegrityStatus()).isEqualTo("COMPLETE");
        verify(exportTaskMapper).updateById(task);
        ArgumentCaptor<BizTodo> todoCaptor = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertThat(todoCaptor.getValue().getSceneCode()).isEqualTo("EXPORT_DONE");
        assertThat(todoCaptor.getValue().getObjectId()).isEqualTo("EXP-1");
        assertThat(todoCaptor.getValue().getStatus()).isEqualTo("UNREAD");
    }

    @Test
    void workerMarksGenerationFailureForRetry() {
        BizExportTask task = task("EXP-2", "PROCESSING");
        when(exportTaskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        doThrow(new IllegalStateException("disk unavailable")).when(exportFileService).generate(task);

        worker.generate(task.getId()).join();

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getIntegrityStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).isEqualTo("disk unavailable");
        assertThat(task.getFinishedAt()).isNotNull();
        verify(exportTaskMapper).updateById(task);
    }

    @Test
    void workerSkipsTaskAlreadyClaimedByAnotherThread() {
        when(exportTaskMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        worker.generate("EXP-3").join();

        verify(exportTaskMapper, never()).selectById("EXP-3");
        verify(exportFileService, never()).generate(any());
    }

    @Test
    void cleanupDeletesExpiredFilesAndMarksTaskExpired() {
        BizExportTask task = task("EXP-4", "SUCCESS");
        task.setExpireAt(LocalDateTime.now().minusMinutes(1));
        task.setFilePath("EXP-4/EXP-4.zip");
        when(exportTaskMapper.selectList(any())).thenReturn(List.of(task));

        cleanupService.cleanupExpiredExports();

        assertThat(task.getStatus()).isEqualTo("EXPIRED");
        assertThat(task.getIntegrityStatus()).isEqualTo("EXPIRED");
        assertThat(task.getFilePath()).isNull();
        assertThat(task.getSizeText()).isEqualTo("0 B");
        verify(exportFileService).deleteTaskFiles(task);
        verify(exportTaskMapper).updateById(task);
    }

    @Test
    void recoveryMarksStaleProcessingTaskFailed() {
        BizExportTask task = task("EXP-5", "PROCESSING");
        task.setUpdatedAt(LocalDateTime.now().minusHours(1));
        when(exportTaskMapper.selectList(any())).thenReturn(List.of(task));
        ReflectionTestUtils.setField(cleanupService, "processingTimeoutMinutes", 30L);

        cleanupService.failStaleProcessingTasks();

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getIntegrityStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).contains("超时");
        assertThat(task.getFinishedAt()).isNotNull();
        verify(exportTaskMapper).updateById(task);
    }

    private BizExportTask task(String id, String status) {
        BizExportTask task = new BizExportTask();
        task.setId(id);
        task.setStatus(status);
        task.setDeleted(0);
        return task;
    }
}
