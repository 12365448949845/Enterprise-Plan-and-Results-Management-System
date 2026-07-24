package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ExportFileService {

    private final PerformanceJsonCodec jsonCodec;
    private final PerformanceDataScopeService dataScopeService;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper evidenceMapper;
    private final BizPlanAdjustmentMapper planAdjustmentMapper;

    @Value("${planning.storage.upload-root:uploads/employee-results}")
    private String uploadRootPath;

    @Value("${planning.storage.export-root:exports}")
    private String exportRootPath;

    public void generate(BizExportTask task) {
        generate(task, resolveOwnerIds(task));
    }

    public void generate(BizExportTask task, Set<Long> ownerIds) {
        try {
            Path taskDirectory = exportRoot().resolve(task.getId()).normalize();
            ensureWithinRoot(taskDirectory);
            Files.createDirectories(taskDirectory);
            Path realRoot = exportRoot().toRealPath();
            taskDirectory = taskDirectory.toRealPath();
            if (!taskDirectory.startsWith(realRoot)) {
                throw new IOException("invalid export path");
            }
            List<String> formats = jsonCodec.stringList(task.getFormats());
            Set<Long> exportOwnerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
            List<String> lines = exportLines(task, exportOwnerIds);
            List<Path> generated = new ArrayList<>();
            if (formats.contains("PDF")) {
                Path pdf = taskDirectory.resolve(task.getId() + ".pdf");
                writePdf(pdf, task, lines);
                generated.add(pdf);
            }
            if (formats.contains("WORD")) {
                Path word = taskDirectory.resolve(task.getId() + ".docx");
                writeWord(word, task, lines);
                generated.add(word);
            }
            boolean packageAsZip = formats.contains("ZIP") || generated.size() != 1
                    || Boolean.TRUE.equals(task.getIncludeEvidence());
            Path output;
            List<String> missingItems = new ArrayList<>();
            if (packageAsZip) {
                output = taskDirectory.resolve(task.getId() + ".zip");
                writeZip(output, task, generated, lines, exportOwnerIds, missingItems);
            } else {
                output = generated.get(0);
            }
            task.setFileName(output.getFileName().toString());
            task.setFilePath(exportRoot().relativize(output).toString().replace('\\', '/'));
            task.setChecksum(sha256(output));
            task.setSizeText(formatSize(Files.size(output)));
            task.setIntegrityStatus(missingItems.isEmpty() ? "COMPLETE" : "INCOMPLETE");
            task.setMissingItems(jsonCodec.write(missingItems));
            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            task.setExpireAt(LocalDateTime.now().plusDays(7));
            task.setErrorMessage(null);
        } catch (IOException ex) {
            task.setStatus("FAILED");
            task.setIntegrityStatus("FAILED");
            task.setErrorMessage("导出文件生成失败：" + ex.getMessage());
            throw new BizException("导出文件生成失败");
        }
    }

    public boolean verify(BizExportTask task) {
        if (task.getFilePath() == null || task.getChecksum() == null) {
            return false;
        }
        try {
            Path file = resolveTaskFile(task);
            return Files.isRegularFile(file) && task.getChecksum().equals(sha256(file));
        } catch (IOException ex) {
            return false;
        }
    }

    public Resource resource(BizExportTask task) {
        try {
            Path file = resolveTaskFile(task);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BizException(404, "导出文件不存在或已过期");
            }
            return resource;
        } catch (IOException ex) {
            throw new BizException(404, "导出文件不存在或已过期");
        }
    }

    public void deleteTaskFiles(BizExportTask task) {
        try {
            Path taskDirectory = exportRoot().resolve(task.getId()).normalize();
            ensureWithinRoot(taskDirectory);
            if (!Files.exists(taskDirectory)) {
                return;
            }
            try (var paths = Files.walk(taskDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // The next cleanup run will retry files still in use.
                    }
                });
            }
        } catch (IOException ignored) {
            // Cleanup must not prevent the scheduler from processing other tasks.
        }
    }

    private List<String> exportLines(BizExportTask task, Set<Long> ownerIds) {
        LocalDate start = task.getPeriodStart() == null ? LocalDate.now().withDayOfMonth(1) : task.getPeriodStart();
        LocalDate end = task.getPeriodEnd() == null ? start.plusMonths(1).minusDays(1) : task.getPeriodEnd();
        Map<Long, SysUser> users = dataScopeService.userMap();
        List<String> lines = new ArrayList<>();
        lines.add("Period: " + start + " to " + end);
        if (ownerIds.isEmpty()) {
            lines.add("No data in the selected scope.");
            return lines;
        }
        String dimensionType = task.getDimensionType();
        if ("DAILY_REVIEW_LIST".equals(dimensionType)) {
            appendDayPlans(lines, users, ownerIds, start, end);
        } else if ("PLAN_ADJUSTMENT_LIST".equals(dimensionType)) {
            appendPlanAdjustments(lines, users, ownerIds, start, end);
        } else if ("MONTH_PLAN_APPROVAL_LIST".equals(dimensionType)) {
            appendMonthPlans(lines, users, ownerIds, start, end);
        } else if (Set.of("RESULT_SUGGESTION_LIST", "RESULT_CONFIRM_LIST").contains(dimensionType)) {
            appendResults(lines, users, resultRows(task, ownerIds, start, end));
        } else {
            appendMonthPlans(lines, users, ownerIds, start, end);
            appendResults(lines, users, resultRows(task, ownerIds, start, end));
        }
        if (lines.size() == 1) {
            lines.add("No data in the selected period.");
        }
        return lines;
    }

    private void appendMonthPlans(List<String> lines, Map<Long, SysUser> users, Set<Long> ownerIds,
                                  LocalDate start, LocalDate end) {
        List<BizMonthPlan> plans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .in(BizMonthPlan::getOwnerUserId, ownerIds)
                .ge(BizMonthPlan::getPlanMonth, start.toString().substring(0, 7))
                .le(BizMonthPlan::getPlanMonth, end.toString().substring(0, 7))
                .orderByAsc(BizMonthPlan::getOwnerUserId)
                .orderByAsc(BizMonthPlan::getPlanMonth));
        for (BizMonthPlan plan : plans) {
            SysUser owner = users.get(plan.getOwnerUserId());
            lines.add("MONTH PLAN | " + (owner == null ? plan.getOwnerUserId() : owner.getRealName())
                    + " | " + plan.getPlanMonth() + " | " + plan.getStatus() + " | " + plan.getTitle());
        }
    }

    private void appendDayPlans(List<String> lines, Map<Long, SysUser> users, Set<Long> ownerIds,
                                LocalDate start, LocalDate end) {
        List<BizDayPlan> plans = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .in(BizDayPlan::getOwnerUserId, ownerIds)
                .ge(BizDayPlan::getPlanDate, start)
                .le(BizDayPlan::getPlanDate, end)
                .orderByAsc(BizDayPlan::getOwnerUserId)
                .orderByAsc(BizDayPlan::getPlanDate));
        for (BizDayPlan plan : plans) {
            SysUser owner = users.get(plan.getOwnerUserId());
            lines.add("DAY PLAN | " + ownerName(owner, plan.getOwnerUserId())
                    + " | " + plan.getPlanDate() + " | " + defaultText(plan.getReviewStatus(), plan.getStatus())
                    + " | " + defaultText(plan.getRiskLevel(), "LOW") + " | " + defaultText(plan.getContent(), ""));
        }
    }

    private void appendPlanAdjustments(List<String> lines, Map<Long, SysUser> users, Set<Long> ownerIds,
                                       LocalDate start, LocalDate end) {
        List<BizPlanAdjustment> adjustments = planAdjustmentMapper.selectList(new LambdaQueryWrapper<BizPlanAdjustment>()
                .eq(BizPlanAdjustment::getDeleted, 0)
                .in(BizPlanAdjustment::getOwnerUserId, ownerIds)
                .ge(BizPlanAdjustment::getCreatedAt, start.atStartOfDay())
                .lt(BizPlanAdjustment::getCreatedAt, end.plusDays(1).atStartOfDay())
                .orderByAsc(BizPlanAdjustment::getOwnerUserId)
                .orderByAsc(BizPlanAdjustment::getCreatedAt));
        for (BizPlanAdjustment adjustment : adjustments) {
            SysUser owner = users.get(adjustment.getOwnerUserId());
            lines.add("PLAN ADJUSTMENT | " + ownerName(owner, adjustment.getOwnerUserId())
                    + " | " + adjustment.getAdjustmentNo() + " | " + adjustment.getStatus()
                    + " | " + defaultText(adjustment.getReason(), ""));
        }
    }

    private List<BizResult> resultRows(BizExportTask task, Set<Long> ownerIds, LocalDate start, LocalDate end) {
        Set<Long> selectedResultIds = selectedResultIds(task);
        return resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .in(BizResult::getOwnerUserId, ownerIds)
                .in(!selectedResultIds.isEmpty(), BizResult::getId, selectedResultIds)
                .ge(BizResult::getResultDate, start)
                .le(BizResult::getResultDate, end)
                .orderByAsc(BizResult::getOwnerUserId)
                .orderByAsc(BizResult::getResultDate));
    }

    private void appendResults(List<String> lines, Map<Long, SysUser> users, List<BizResult> results) {
        for (BizResult result : results) {
            SysUser owner = users.get(result.getOwnerUserId());
            lines.add("RESULT | " + ownerName(owner, result.getOwnerUserId())
                    + " | " + result.getResultDate() + " | " + result.getStatus() + " | "
                    + result.getCompletionRate() + "% | " + result.getTitle());
        }
    }

    private void writePdf(Path target, BizExportTask task, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadPdfFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = 800;
            content.beginText();
            content.setFont(font, 16);
            content.newLineAtOffset(50, y);
            content.showText(pdfText(task.getDimensionName(), font));
            content.endText();
            y -= 30;
            for (String line : lines) {
                if (y < 55) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = 800;
                }
                content.beginText();
                content.setFont(font, 10);
                content.newLineAtOffset(50, y);
                content.showText(pdfText(truncate(line, 95), font));
                content.endText();
                y -= 18;
            }
            content.close();
            document.save(target.toFile());
        }
    }

    private PDFont loadPdfFont(PDDocument document) throws IOException {
        for (Path candidate : List.of(Paths.get("C:/Windows/Fonts/simhei.ttf"), Paths.get("C:/Windows/Fonts/simsun.ttc"))) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return PDType0Font.load(document, candidate.toFile());
                } catch (IOException ignored) {
                    // Try the next installed font.
                }
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String pdfText(String text, PDFont font) {
        if (font instanceof PDType0Font) {
            return text;
        }
        return text.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private void writeWord(Path target, BizExportTask task, List<String> lines) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(target)) {
            XWPFParagraph title = document.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(task.getDimensionName());
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            document.write(output);
        }
    }

    private void writeZip(Path target, BizExportTask task, List<Path> generated, List<String> lines,
                          Set<Long> ownerIds, List<String> missingItems) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            for (Path file : generated) {
                addFile(zip, file, file.getFileName().toString());
            }
            zip.putNextEntry(new ZipEntry("manifest.txt"));
            String manifest = task.getDimensionName() + System.lineSeparator() + String.join(System.lineSeparator(), lines);
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (Boolean.TRUE.equals(task.getIncludeEvidence())) {
                addEvidenceFiles(zip, task, ownerIds, missingItems);
            }
        }
    }

    private void addEvidenceFiles(ZipOutputStream zip, BizExportTask task, Set<Long> ownerIds,
                                  List<String> missingItems) throws IOException {
        LocalDate start = task.getPeriodStart() == null ? LocalDate.now().withDayOfMonth(1) : task.getPeriodStart();
        LocalDate end = task.getPeriodEnd() == null ? start.plusMonths(1).minusDays(1) : task.getPeriodEnd();
        if (ownerIds.isEmpty()) {
            return;
        }
        List<BizResult> results = resultRows(task, ownerIds, start, end);
        if (results.isEmpty()) {
            return;
        }
        Map<Long, BizResult> resultMap = new HashMap<>();
        results.forEach(result -> resultMap.put(result.getId(), result));
        List<BizResultEvidence> evidences = evidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                .eq(BizResultEvidence::getDeleted, 0)
                .in(BizResultEvidence::getResultId, resultMap.keySet()));
        Path uploadRoot = Paths.get(uploadRootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(uploadRoot)) {
            results.forEach(result -> missingItems.add("成果 " + result.getId() + "：证据目录不存在"));
            return;
        }
        Path realUploadRoot = uploadRoot.toRealPath();
        Map<Long, Integer> evidenceCounts = new HashMap<>();
        for (BizResultEvidence evidence : evidences) {
            evidenceCounts.merge(evidence.getResultId(), 1, Integer::sum);
            String itemName = "成果 " + evidence.getResultId() + " / " + safeZipFileName(evidence);
            Path candidate = StringUtils.hasText(evidence.getFileUrl())
                    ? uploadRoot.resolve(evidence.getFileUrl()).normalize() : null;
            if (candidate == null || !candidate.startsWith(uploadRoot) || !Files.isRegularFile(candidate)) {
                missingItems.add(itemName + "：文件不存在");
                continue;
            }
            Path realFile;
            try {
                realFile = candidate.toRealPath();
            } catch (IOException ex) {
                missingItems.add(itemName + "：文件不存在");
                continue;
            }
            if (!realFile.startsWith(realUploadRoot) || !Files.isReadable(realFile)) {
                missingItems.add(itemName + "：文件路径无效");
                continue;
            }
            if (StringUtils.hasText(evidence.getChecksum())
                    && !evidence.getChecksum().equalsIgnoreCase(sha256(realFile))) {
                missingItems.add(itemName + "：完整性校验失败");
                continue;
            }
            addFile(zip, realFile, "evidence/" + evidence.getResultId() + "/"
                    + evidence.getId() + "-" + safeZipFileName(evidence));
        }
        for (BizResult result : results) {
            if (!evidenceCounts.containsKey(result.getId())) {
                missingItems.add("成果 " + result.getId() + "：未上传证据");
            }
        }
    }

    private void addFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private String safeZipFileName(BizResultEvidence evidence) {
        String fallback = "evidence-" + evidence.getId();
        if (!StringUtils.hasText(evidence.getFileName())) {
            return fallback;
        }
        try {
            Path fileName = Paths.get(evidence.getFileName()).getFileName();
            String value = fileName == null ? "" : fileName.toString().trim();
            return StringUtils.hasText(value) ? value.replace('/', '_').replace('\\', '_') : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private Path resolveTaskFile(BizExportTask task) throws IOException {
        Path root = exportRoot();
        Path file = root.resolve(task.getFilePath()).normalize();
        ensureWithinRoot(file);
        if (!Files.isRegularFile(file)) {
            throw new IOException("export file not found");
        }
        Path realRoot = root.toRealPath();
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new IOException("invalid export path");
        }
        return realFile;
    }

    private Path exportRoot() throws IOException {
        Path root = Paths.get(exportRootPath).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private void ensureWithinRoot(Path path) throws IOException {
        if (!path.startsWith(exportRoot())) {
            throw new IOException("invalid export path");
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder value = new StringBuilder();
            for (byte item : digest.digest()) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }

    private String formatSize(long size) {
        if (size >= 1024 * 1024) {
            return String.format("%.2f MB", size / 1024.0 / 1024.0);
        }
        if (size >= 1024) {
            return String.format("%.2f KB", size / 1024.0);
        }
        return size + " B";
    }

    private Set<Long> resolveOwnerIds(BizExportTask task) {
        if ("PERSON_LEDGER".equals(task.getDimensionType())) {
            return Set.of(personalOwnerId(task));
        }
        Set<Long> selectedIds = selectedResultIds(task);
        if (!selectedIds.isEmpty()) {
            Set<Long> allowedDeptIds = task.getDeptId() == null
                    ? Set.of() : dataScopeService.departmentScope(task.getDeptId());
            Set<Long> ownerIds = new HashSet<>();
            for (BizResult result : resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                    .eq(BizResult::getDeleted, 0)
                    .in(BizResult::getId, selectedIds))) {
                if (allowedDeptIds.isEmpty() || allowedDeptIds.contains(result.getDeptId())) {
                    ownerIds.add(result.getOwnerUserId());
                }
            }
            return ownerIds;
        }
        Long rootDeptId = scopedOrgId(task);
        if (rootDeptId == null) {
            return Set.of();
        }
        Set<Long> deptIds = new HashSet<>(dataScopeService.departmentScope(rootDeptId));
        if (task.getDeptId() != null) {
            deptIds.retainAll(dataScopeService.departmentScope(task.getDeptId()));
        }
        Set<Long> ownerIds = new HashSet<>();
        for (SysUser user : dataScopeService.userMap().values()) {
            if (deptIds.contains(user.getDeptId())) {
                ownerIds.add(user.getId());
            }
        }
        return ownerIds;
    }

    private Long scopedOrgId(BizExportTask task) {
        if (StringUtils.hasText(task.getDimensionId()) && task.getDimensionId().matches("\\d+")) {
            return Long.valueOf(task.getDimensionId());
        }
        return task.getDeptId();
    }

    private Set<Long> selectedResultIds(BizExportTask task) {
        if (!StringUtils.hasText(task.getDimensionId()) || !task.getDimensionId().startsWith("RESULTS:")) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        try {
            for (String value : task.getDimensionId().substring("RESULTS:".length()).split(",")) {
                if (StringUtils.hasText(value)) {
                    ids.add(Long.valueOf(value.trim()));
                }
            }
        } catch (NumberFormatException ex) {
            throw new BizException("成果编号格式错误");
        }
        return ids;
    }

    private Long personalOwnerId(BizExportTask task) {
        if (StringUtils.hasText(task.getDimensionId())) {
            try {
                return Long.valueOf(task.getDimensionId());
            } catch (NumberFormatException ex) {
                throw new BizException("个人导出对象编号格式错误");
            }
        }
        return task.getRequestedBy();
    }

    private String ownerName(SysUser owner, Long ownerId) {
        return owner == null ? String.valueOf(ownerId) : owner.getRealName();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
