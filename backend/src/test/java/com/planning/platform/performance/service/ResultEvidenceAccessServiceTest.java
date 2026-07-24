package com.planning.platform.performance.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultEvidenceAccessServiceTest {

    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper evidenceMapper;
    @Mock
    private PerformanceRoleGuard roleGuard;
    @Mock
    private PerformanceDataScopeService dataScopeService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ResultEvidenceAccessService service;

    @TempDir
    Path uploadRoot;

    private final AuthUser leader = new AuthUser(20L, "leader", "直属领导", 110L, 110L,
            false, List.of("DIRECT_LEADER"), List.of());

    @Test
    void leaderDownloadsEvidenceAfterScopeAndChecksumValidation() throws Exception {
        ReflectionTestUtils.setField(service, "uploadRootPath", uploadRoot.toString());
        byte[] content = "verified evidence".getBytes();
        Path file = uploadRoot.resolve("71/evidence.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        BizResult result = result(71L, 10L);
        BizResultEvidence evidence = evidence(72L, result.getId(), "71/evidence.pdf");
        evidence.setChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(evidenceMapper.selectById(evidence.getId())).thenReturn(evidence);

        var response = service.downloadForLeader(leader, result.getId(), evidence.getId());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().exists()).isTrue();
        verify(roleGuard).requireLeaderModule(leader);
        verify(dataScopeService).requireLeaderOwner(leader, result.getOwnerUserId());
    }

    @Test
    void tamperedEvidenceIsRejectedBeforeDownload() throws Exception {
        ReflectionTestUtils.setField(service, "uploadRootPath", uploadRoot.toString());
        Path file = uploadRoot.resolve("81/evidence.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "tampered");
        BizResult result = result(81L, 10L);
        BizResultEvidence evidence = evidence(82L, result.getId(), "81/evidence.pdf");
        evidence.setChecksum("0000");
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(evidenceMapper.selectById(evidence.getId())).thenReturn(evidence);

        BizException error = catchThrowableOfType(
                () -> service.downloadForLeader(leader, result.getId(), evidence.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("完整性校验失败");
    }

    @Test
    void evidencePathCannotEscapeConfiguredUploadRoot() {
        ReflectionTestUtils.setField(service, "uploadRootPath", uploadRoot.toString());
        BizResult result = result(91L, 10L);
        BizResultEvidence evidence = evidence(92L, result.getId(), "../outside.pdf");
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(evidenceMapper.selectById(evidence.getId())).thenReturn(evidence);

        BizException error = catchThrowableOfType(
                () -> service.downloadForLeader(leader, result.getId(), evidence.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(404);
    }

    private BizResult result(Long id, Long ownerId) {
        BizResult result = new BizResult();
        result.setId(id);
        result.setOwnerUserId(ownerId);
        result.setDeleted(0);
        return result;
    }

    private BizResultEvidence evidence(Long id, Long resultId, String fileUrl) {
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(id);
        evidence.setResultId(resultId);
        evidence.setFileName("evidence.pdf");
        evidence.setFileUrl(fileUrl);
        evidence.setDeleted(0);
        return evidence;
    }
}
