package com.planning.platform.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class EmployeeAppealPackageService {

    private final BizEmployeeAppealMapper appealMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper evidenceMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final AuditLogService auditLogService;

    @Value("${planning.storage.upload-root:uploads/employee-results}")
    private String uploadRootPath;

    @Value("${planning.storage.export-root:exports}")
    private String exportRootPath;

    public ResponseEntity<Resource> download(AuthUser user, Long appealId) {
        return download(user, appealId, false);
    }

    public ResponseEntity<Resource> downloadForHandler(AuthUser user, Long appealId) {
        return download(user, appealId, true);
    }

    private ResponseEntity<Resource> download(AuthUser user, Long appealId, boolean handlerAccess) {
        BizEmployeeAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null || Integer.valueOf(1).equals(appeal.getDeleted())) {
            throw new BizException(404, "申诉记录不存在");
        }
        if (handlerAccess) {
            boolean superAdmin = user.roles() != null && user.roles().contains("SUPER_ADMIN");
            boolean disputeAccess = user.roles() != null
                    && user.roles().stream().anyMatch(role -> role.equals("SUPER_ADMIN")
                    || role.equals("DEPT_OWNER") || role.equals("DEPT_LEADER")
                    || role.equals("PROJECT_MANAGER") || role.equals("DIRECT_LEADER")
                    || role.equals("EMPLOYEE"));
            if (!superAdmin && !disputeAccess && !user.userId().equals(appeal.getHandlerId())) {
                throw new BizException(403, "只能由当前申诉处理人下载资料包");
            }
        } else if (!user.userId().equals(appeal.getOwnerUserId())) {
            throw new BizException(403, "只能下载本人的申诉资料包");
        }

        BizResult result = appeal.getRelatedResultId() == null ? null : resultMapper.selectById(appeal.getRelatedResultId());
        BizMonthPlan plan = result == null || result.getPlanId() == null ? null : monthPlanMapper.selectById(result.getPlanId());
        List<BizMonthPlanItem> planItems = plan == null ? List.of() : monthPlanItemMapper.selectList(
                new LambdaQueryWrapper<BizMonthPlanItem>()
                        .eq(BizMonthPlanItem::getDeleted, 0)
                        .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                        .orderByAsc(BizMonthPlanItem::getSortNo)
                        .orderByAsc(BizMonthPlanItem::getId));
        List<BizResultEvidence> evidences = result == null ? List.of() : evidenceMapper.selectList(
                new LambdaQueryWrapper<BizResultEvidence>()
                        .eq(BizResultEvidence::getDeleted, 0)
                        .eq(BizResultEvidence::getResultId, result.getId())
                        .orderByAsc(BizResultEvidence::getId));

        Path packageFile = writePackage(user, appeal, result, plan, planItems, evidences);
        try {
            Resource resource = new UrlResource(packageFile.toUri());
            auditLogService.success(user, handlerAccess ? "DEPARTMENT_APPEAL_PACKAGE_DOWNLOAD" : "EMPLOYEE_APPEAL_PACKAGE_DOWNLOAD",
                    "EMPLOYEE_APPEAL", appeal.getId(),
                    "appealNo=" + appeal.getAppealNo() + ", evidenceCount=" + evidences.size());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + appeal.getAppealNo() + ".zip\"")
                    .body(resource);
        } catch (IOException ex) {
            throw new BizException("申诉资料包读取失败");
        }
    }

    private Path writePackage(AuthUser user, BizEmployeeAppeal appeal, BizResult result, BizMonthPlan plan,
                              List<BizMonthPlanItem> planItems, List<BizResultEvidence> evidences) {
        try {
            Path root = Paths.get(exportRootPath).toAbsolutePath().normalize();
            Path directory = root.resolve("employee-appeals").resolve(String.valueOf(user.userId())).normalize();
            if (!directory.startsWith(root)) {
                throw new BizException("申诉资料包路径无效");
            }
            Files.createDirectories(directory);
            Path output = directory.resolve(appeal.getAppealNo() + ".zip");
            Path temporary = directory.resolve(appeal.getAppealNo() + "-" + UUID.randomUUID() + ".part");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                addText(zip, "manifest.txt", manifest(appeal, result, plan, planItems, evidences));
                addEvidenceFiles(zip, evidences);
            }
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return output;
        } catch (IOException ex) {
            throw new BizException("申诉资料包生成失败");
        }
    }

    private String manifest(BizEmployeeAppeal appeal, BizResult result, BizMonthPlan plan,
                            List<BizMonthPlanItem> planItems, List<BizResultEvidence> evidences) {
        StringBuilder text = new StringBuilder();
        text.append("申诉编号: ").append(appeal.getAppealNo()).append(System.lineSeparator());
        text.append("申诉标题: ").append(appeal.getTitle()).append(System.lineSeparator());
        text.append("申诉状态: ").append(appeal.getStatus()).append(System.lineSeparator());
        text.append("申诉原因: ").append(appeal.getReason()).append(System.lineSeparator());
        text.append("发起时间: ").append(appeal.getCreatedAt()).append(System.lineSeparator());
        text.append("处理意见: ").append(defaultText(appeal.getHandleComment())).append(System.lineSeparator());
        text.append("处理时间: ").append(defaultText(appeal.getHandledAt())).append(System.lineSeparator());
        if (result != null) {
            text.append(System.lineSeparator()).append("[关联成果]").append(System.lineSeparator());
            text.append("成果编号: ").append(result.getId()).append(System.lineSeparator());
            text.append("成果标题: ").append(result.getTitle()).append(System.lineSeparator());
            text.append("成果版本: ").append(defaultText(result.getVersionNo())).append(System.lineSeparator());
            text.append("完成比例: ").append(result.getCompletionRate()).append('%').append(System.lineSeparator());
            text.append("成果状态: ").append(result.getStatus()).append(System.lineSeparator());
            text.append("成果说明: ").append(defaultText(result.getContent())).append(System.lineSeparator());
            text.append("直属领导建议: ").append(defaultText(result.getLeaderSuggestion())).append(System.lineSeparator());
            text.append("最终确认意见: ").append(defaultText(result.getConfirmComment())).append(System.lineSeparator());
            text.append("确认记录编号: ").append(defaultText(result.getVerifyRecordId())).append(System.lineSeparator());
        }
        if (plan != null) {
            text.append(System.lineSeparator()).append("[关联月计划]").append(System.lineSeparator());
            text.append("计划编号: ").append(plan.getId()).append(System.lineSeparator());
            text.append("计划月份: ").append(plan.getPlanMonth()).append(System.lineSeparator());
            text.append("计划标题: ").append(plan.getTitle()).append(System.lineSeparator());
            text.append("计划状态: ").append(plan.getStatus()).append(System.lineSeparator());
            text.append("审批意见: ").append(defaultText(plan.getApprovalComment())).append(System.lineSeparator());
            for (int index = 0; index < planItems.size(); index++) {
                BizMonthPlanItem item = planItems.get(index);
                text.append("计划明细 ").append(index + 1).append(": ")
                        .append(defaultText(item.getTaskName())).append(" | ")
                        .append(defaultText(item.getDeliverable())).append(System.lineSeparator());
            }
        }
        text.append(System.lineSeparator()).append("[证据文件]").append(System.lineSeparator());
        if (evidences.isEmpty()) {
            text.append("无证据附件").append(System.lineSeparator());
        } else {
            for (BizResultEvidence evidence : evidences) {
                text.append(evidence.getFileName()).append(" | ")
                        .append(defaultText(evidence.getStatus())).append(" | SHA-256: ")
                        .append(defaultText(evidence.getChecksum())).append(System.lineSeparator());
            }
        }
        return text.toString();
    }

    private void addEvidenceFiles(ZipOutputStream zip, List<BizResultEvidence> evidences) throws IOException {
        Path uploadRoot = Paths.get(uploadRootPath).toAbsolutePath().normalize();
        for (BizResultEvidence evidence : evidences) {
            if (!StringUtils.hasText(evidence.getFileUrl())) {
                continue;
            }
            Path file = uploadRoot.resolve(evidence.getFileUrl()).normalize();
            if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
                continue;
            }
            String fileName = Paths.get(evidence.getFileName()).getFileName().toString();
            zip.putNextEntry(new ZipEntry("evidence/" + evidence.getId() + "-" + fileName));
            Files.copy(file, zip);
            zip.closeEntry();
        }
    }

    private void addText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String defaultText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
