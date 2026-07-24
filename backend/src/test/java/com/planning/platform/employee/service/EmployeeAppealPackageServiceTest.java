package com.planning.platform.employee.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAppealPackageServiceTest {

    @Mock
    private BizEmployeeAppealMapper appealMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper evidenceMapper;
    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private EmployeeAppealPackageService packageService;

    @TempDir
    private Path tempDirectory;

    private final AuthUser employee = new AuthUser(10L, "employee", "演示员工", 110L, 20L,
            false, List.of("EMPLOYEE"), List.of());

    @Test
    void generatesAppealManifestAndIncludesEvidenceFile() throws Exception {
        Path uploadRoot = tempDirectory.resolve("uploads");
        Path exportRoot = tempDirectory.resolve("exports");
        Path evidenceFile = uploadRoot.resolve("3/result.pdf");
        Files.createDirectories(evidenceFile.getParent());
        Files.writeString(evidenceFile, "evidence", StandardCharsets.UTF_8);
        ReflectionTestUtils.setField(packageService, "uploadRootPath", uploadRoot.toString());
        ReflectionTestUtils.setField(packageService, "exportRootPath", exportRoot.toString());

        BizEmployeeAppeal appeal = appeal(1L, employee.userId());
        BizResult result = new BizResult();
        result.setId(3L);
        result.setPlanId(2L);
        result.setTitle("成果报告");
        result.setVersionNo("V1");
        result.setCompletionRate(100);
        result.setStatus("CONFIRMED");
        result.setContent("成果说明");
        result.setLeaderSuggestion("建议确认");
        result.setConfirmComment("验收通过");
        result.setVerifyRecordId("VERIFY-1");
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(2L);
        plan.setPlanMonth("2026-07");
        plan.setTitle("七月计划");
        plan.setStatus("APPROVED");
        plan.setApprovalComment("同意执行");
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setTaskName("任务一");
        item.setDeliverable("报告");
        item.setAcceptanceStandard("评审通过");
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(31L);
        evidence.setResultId(3L);
        evidence.setFileName("result.pdf");
        evidence.setFileUrl("3/result.pdf");
        evidence.setStatus("REVIEW_PASSED");
        evidence.setChecksum("abc123");
        evidence.setDeleted(0);

        when(appealMapper.selectById(1L)).thenReturn(appeal);
        when(resultMapper.selectById(3L)).thenReturn(result);
        when(monthPlanMapper.selectById(2L)).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        Resource resource = packageService.download(employee, 1L).getBody();

        assertThat(resource).isNotNull();
        Path packageFile = resource.getFile().toPath();
        assertThat(packageFile).isRegularFile();
        try (ZipFile zip = new ZipFile(packageFile.toFile(), StandardCharsets.UTF_8)) {
            assertThat(zip.getEntry("manifest.txt")).isNotNull();
            assertThat(zip.getEntry("evidence/31-result.pdf")).isNotNull();
            String manifest = new String(zip.getInputStream(zip.getEntry("manifest.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(manifest).contains("AP20260714001", "成果报告", "验收通过", "result.pdf");
        }
        verify(auditLogService).success(eq(employee), eq("EMPLOYEE_APPEAL_PACKAGE_DOWNLOAD"), eq("EMPLOYEE_APPEAL"), eq(1L),
                contains("evidenceCount=1"));
    }

    @Test
    void rejectsDownloadingAnotherEmployeesAppeal() {
        when(appealMapper.selectById(1L)).thenReturn(appeal(1L, 99L));

        BizException error = catchThrowableOfType(() -> packageService.download(employee, 1L), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
    }

    private BizEmployeeAppeal appeal(Long id, Long ownerId) {
        BizEmployeeAppeal appeal = new BizEmployeeAppeal();
        appeal.setId(id);
        appeal.setAppealNo("AP20260714001");
        appeal.setTitle("成果确认申诉");
        appeal.setReason("确认意见与证据不一致");
        appeal.setStatus("SUBMITTED");
        appeal.setOwnerUserId(ownerId);
        appeal.setRelatedResultId(3L);
        appeal.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        appeal.setDeleted(0);
        return appeal;
    }
}
