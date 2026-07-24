package com.planning.platform.performance.service;

import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ExportFileServiceTest {

    @Mock
    private PerformanceJsonCodec jsonCodec;
    @Mock
    private PerformanceDataScopeService dataScopeService;
    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizDayPlanMapper dayPlanMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper evidenceMapper;
    @Mock
    private BizPlanAdjustmentMapper planAdjustmentMapper;

    @InjectMocks
    private ExportFileService exportFileService;

    @TempDir
    private Path tempDirectory;

    @Test
    void generatesPdfWordAndZipWithVerifiableChecksum() throws Exception {
        Path exportRoot = tempDirectory.resolve("exports");
        Path uploadRoot = tempDirectory.resolve("uploads");
        ReflectionTestUtils.setField(exportFileService, "exportRootPath", exportRoot.toString());
        ReflectionTestUtils.setField(exportFileService, "uploadRootPath", uploadRoot.toString());
        when(jsonCodec.stringList("formats")).thenReturn(List.of("PDF", "WORD", "ZIP"));
        when(dataScopeService.departmentScope(110L)).thenReturn(Set.of(110L));
        SysUser employee = new SysUser();
        employee.setId(10L);
        employee.setDeptId(110L);
        employee.setRealName("员工");
        when(dataScopeService.userMap()).thenReturn(Map.of(employee.getId(), employee));
        when(monthPlanMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());

        BizExportTask task = new BizExportTask();
        task.setId("EXP-FILE-1");
        task.setDimensionType("DEPARTMENT_LEDGER");
        task.setDimensionName("Department ledger");
        task.setPeriodStart(LocalDate.of(2026, 7, 1));
        task.setPeriodEnd(LocalDate.of(2026, 7, 31));
        task.setFormats("formats");
        task.setIncludeEvidence(false);
        task.setDeptId(110L);

        exportFileService.generate(task);

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getIntegrityStatus()).isEqualTo("COMPLETE");
        assertThat(task.getChecksum()).hasSize(64);
        assertThat(task.getExpireAt()).isNotNull();
        assertThat(exportFileService.verify(task)).isTrue();
        Path output = exportRoot.resolve(task.getFilePath());
        assertThat(output).isRegularFile();
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertThat(zip.getEntry("EXP-FILE-1.pdf")).isNotNull();
            assertThat(zip.getEntry("EXP-FILE-1.docx")).isNotNull();
            assertThat(zip.getEntry("manifest.txt")).isNotNull();
        }

        exportFileService.deleteTaskFiles(task);
        assertThat(Files.exists(exportRoot.resolve(task.getId()))).isFalse();
    }

    @Test
    void dailyReviewExportUsesDayPlanRowsWithinOwnerSnapshot() {
        Path exportRoot = tempDirectory.resolve("daily-exports");
        ReflectionTestUtils.setField(exportFileService, "exportRootPath", exportRoot.toString());
        ReflectionTestUtils.setField(exportFileService, "uploadRootPath", tempDirectory.resolve("uploads").toString());
        when(jsonCodec.stringList("pdf-only")).thenReturn(List.of("PDF"));
        SysUser employee = new SysUser();
        employee.setId(10L);
        employee.setRealName("员工");
        when(dataScopeService.userMap()).thenReturn(Map.of(employee.getId(), employee));
        BizDayPlan dayPlan = new BizDayPlan();
        dayPlan.setId(20L);
        dayPlan.setOwnerUserId(employee.getId());
        dayPlan.setPlanDate(LocalDate.of(2026, 7, 15));
        dayPlan.setContent("完成接口联调");
        dayPlan.setReviewStatus("COMMENTED");
        dayPlan.setRiskLevel("LOW");
        dayPlan.setDeleted(0);
        when(dayPlanMapper.selectList(any())).thenReturn(List.of(dayPlan));
        BizExportTask task = new BizExportTask();
        task.setId("EXP-DAILY-1");
        task.setDimensionType("DAILY_REVIEW_LIST");
        task.setDimensionName("日计划点评清单");
        task.setPeriodStart(LocalDate.of(2026, 7, 1));
        task.setPeriodEnd(LocalDate.of(2026, 7, 31));
        task.setFormats("pdf-only");
        task.setIncludeEvidence(false);

        exportFileService.generate(task, Set.of(employee.getId()));

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        verify(dayPlanMapper).selectList(any());
        verifyNoInteractions(monthPlanMapper, resultMapper, planAdjustmentMapper);
    }

    @Test
    void tamperedEvidenceIsOmittedAndRecordedAsIncomplete() throws Exception {
        Path exportRoot = tempDirectory.resolve("evidence-exports");
        Path uploadRoot = tempDirectory.resolve("evidence-uploads");
        Path evidenceFile = uploadRoot.resolve("31/result.pdf");
        Files.createDirectories(evidenceFile.getParent());
        Files.writeString(evidenceFile, "tampered evidence");
        ReflectionTestUtils.setField(exportFileService, "exportRootPath", exportRoot.toString());
        ReflectionTestUtils.setField(exportFileService, "uploadRootPath", uploadRoot.toString());
        when(jsonCodec.stringList("zip-only")).thenReturn(List.of("ZIP"));
        when(jsonCodec.write(any())).thenAnswer(invocation -> invocation.getArgument(0).toString());

        SysUser employee = new SysUser();
        employee.setId(10L);
        employee.setRealName("员工");
        when(dataScopeService.userMap()).thenReturn(Map.of(employee.getId(), employee));
        BizResult result = new BizResult();
        result.setId(31L);
        result.setOwnerUserId(employee.getId());
        result.setResultDate(LocalDate.of(2026, 7, 15));
        result.setStatus("PENDING");
        result.setCompletionRate(90);
        result.setTitle("阶段成果");
        result.setDeleted(0);
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(32L);
        evidence.setResultId(result.getId());
        evidence.setFileName("result.pdf");
        evidence.setFileUrl("31/result.pdf");
        evidence.setChecksum("0000");
        evidence.setDeleted(0);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        BizExportTask task = new BizExportTask();
        task.setId("EXP-EVIDENCE-1");
        task.setDimensionType("RESULT_CONFIRM_LIST");
        task.setDimensionName("成果确认资料包");
        task.setPeriodStart(LocalDate.of(2026, 7, 1));
        task.setPeriodEnd(LocalDate.of(2026, 7, 31));
        task.setFormats("zip-only");
        task.setIncludeEvidence(true);

        exportFileService.generate(task, Set.of(employee.getId()));

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getIntegrityStatus()).isEqualTo("INCOMPLETE");
        assertThat(task.getMissingItems()).contains("完整性校验失败");
        try (ZipFile zip = new ZipFile(exportRoot.resolve(task.getFilePath()).toFile())) {
            assertThat(zip.stream().map(entry -> entry.getName()))
                    .noneMatch(name -> name.startsWith("evidence/"));
        }
    }
}
