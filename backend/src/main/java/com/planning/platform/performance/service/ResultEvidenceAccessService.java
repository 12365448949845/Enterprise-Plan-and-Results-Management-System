package com.planning.platform.performance.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class ResultEvidenceAccessService {

    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper evidenceMapper;
    private final PerformanceRoleGuard roleGuard;
    private final PerformanceDataScopeService dataScopeService;
    private final AuditLogService auditLogService;

    @Value("${planning.storage.upload-root:uploads/employee-results}")
    private String uploadRootPath;

    public ResponseEntity<Resource> downloadForLeader(AuthUser user, Long resultId, Long evidenceId) {
        roleGuard.requireLeaderModule(user);
        BizResult result = requireResult(resultId);
        dataScopeService.requireLeaderOwner(user, result.getOwnerUserId());
        return download(user, result, requireEvidence(resultId, evidenceId), "LEADER_RESULT_EVIDENCE_DOWNLOAD");
    }

    public ResponseEntity<Resource> downloadForDepartment(AuthUser user, Long resultId, Long evidenceId) {
        roleGuard.requireDepartmentModule(user);
        BizResult result = requireResult(resultId);
        dataScopeService.requireDepartmentOwner(user, result.getOwnerUserId());
        return download(user, result, requireEvidence(resultId, evidenceId), "DEPARTMENT_RESULT_EVIDENCE_DOWNLOAD");
    }

    private ResponseEntity<Resource> download(AuthUser user, BizResult result, BizResultEvidence evidence,
                                               String auditAction) {
        Path file = resolveEvidenceFile(evidence.getFileUrl());
        verifyChecksum(file, evidence.getChecksum());
        Resource resource = new FileSystemResource(file);
        String fileName = StringUtils.hasText(evidence.getFileName())
                ? Paths.get(evidence.getFileName()).getFileName().toString()
                : "evidence-" + evidence.getId();
        auditLogService.success(user, auditAction, "RESULT_EVIDENCE", evidence.getId(),
                "resultId=" + result.getId() + ", fileName=" + fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build().toString())
                .body(resource);
    }

    private Path resolveEvidenceFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BizException(404, "成果证据文件不存在");
        }
        try {
            Path root = Paths.get(uploadRootPath).toAbsolutePath().normalize();
            Path candidate = root.resolve(fileUrl).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
                throw new BizException(404, "成果证据文件不存在");
            }
            Path realRoot = root.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile) || !Files.isReadable(realFile)) {
                throw new BizException(404, "成果证据文件不存在");
            }
            return realFile;
        } catch (IOException ex) {
            throw new BizException(404, "成果证据文件不存在");
        }
    }

    private BizResult requireResult(Long resultId) {
        BizResult result = resultMapper.selectById(resultId);
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果记录不存在");
        }
        return result;
    }

    private BizResultEvidence requireEvidence(Long resultId, Long evidenceId) {
        BizResultEvidence evidence = evidenceMapper.selectById(evidenceId);
        if (evidence == null || Integer.valueOf(1).equals(evidence.getDeleted())
                || !resultId.equals(evidence.getResultId()) || !StringUtils.hasText(evidence.getFileUrl())) {
            throw new BizException(404, "成果证据不存在");
        }
        return evidence;
    }

    private void verifyChecksum(Path file, String expectedChecksum) {
        if (!StringUtils.hasText(expectedChecksum)) {
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) {
                actual.append(String.format("%02x", value));
            }
            if (!expectedChecksum.equalsIgnoreCase(actual.toString())) {
                throw new BizException(409, "成果证据文件完整性校验失败");
            }
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new BizException("成果证据文件校验失败");
        }
    }
}
