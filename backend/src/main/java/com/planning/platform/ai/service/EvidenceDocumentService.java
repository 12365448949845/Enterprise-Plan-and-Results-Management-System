package com.planning.platform.ai.service;

import com.planning.platform.ai.config.AiProperties;
import com.planning.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class EvidenceDocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 80;
    private static final int MAX_ZIP_EXPANDED_BYTES = 30 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "doc", "docx", "zip");
    private static final Set<String> ZIP_TEXT_TYPES = Set.of("txt", "md", "csv", "json", "xml", "log");

    private final AiProperties properties;

    public EvidenceDocument inspect(MultipartFile file) {
        validateBasic(file);
        try {
            String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String extension = extension(originalName);
            byte[] bytes = file.getBytes();
            validateContent(extension, bytes);
            String extracted = switch (extension) {
                case "pdf" -> extractPdf(bytes, originalName);
                case "doc" -> extractDoc(bytes, originalName);
                case "docx" -> extractDocx(bytes, originalName);
                case "zip" -> extractZip(bytes, originalName);
                default -> "";
            };
            int limit = Math.max(2000, properties.getMaxEvidenceCharacters());
            boolean truncated = extracted.length() > limit;
            String text = truncated ? extracted.substring(0, limit) : extracted;
            return new EvidenceDocument(originalName, extension, bytes.length, sha256(bytes), text, truncated,
                    StringUtils.hasText(text));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(422, "成果证据解析失败：" + safeMessage(ex));
        }
    }

    public void validate(MultipartFile file) {
        inspect(file);
    }

    private void validateBasic(MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BizException(422, "请上传成果证据文件");
        }
        String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = extension(originalName);
        if (!SUPPORTED_TYPES.contains(extension)) {
            throw new BizException(422, "成果附件仅支持 PDF、Word、Zip");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(422, "成果附件大小不能超过 20MB");
        }
    }

    private void validateContent(String extension, byte[] bytes) {
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'}) && validPdf(bytes);
            case "doc" -> startsWith(bytes, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "docx" -> validDocx(bytes);
            case "zip" -> validZip(bytes);
            default -> false;
        };
        if (!valid) {
            throw new BizException(422, "成果附件内容与文件类型不匹配或文件已损坏");
        }
    }

    private String extractPdf(byte[] bytes, String source) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder result = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();
                if (StringUtils.hasText(pageText)) {
                    result.append("[证据:").append(source).append(" 第").append(page).append("页]\n")
                            .append(pageText).append("\n");
                }
            }
            return result.toString();
        }
    }

    private String extractDoc(byte[] bytes, String source) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            StringBuilder result = new StringBuilder();
            String[] paragraphs = extractor.getParagraphText();
            for (int index = 0; index < paragraphs.length; index++) {
                String text = paragraphs[index] == null ? "" : paragraphs[index].trim();
                if (StringUtils.hasText(text)) {
                    result.append("[证据:").append(source).append(" 段落").append(index + 1).append("]\n")
                            .append(text).append("\n");
                }
            }
            return result.toString();
        }
    }

    private String extractDocx(byte[] bytes, String source) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder result = new StringBuilder();
            int paragraphNo = 1;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
                if (StringUtils.hasText(text)) {
                    result.append("[证据:").append(source).append(" 段落").append(paragraphNo).append("]\n")
                            .append(text).append("\n");
                }
                paragraphNo++;
            }
            int tableNo = 1;
            for (XWPFTable table : document.getTables()) {
                int rowNo = 1;
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (StringUtils.hasText(cell.getText())) {
                            cells.add(cell.getText().trim());
                        }
                    }
                    if (!cells.isEmpty()) {
                        result.append("[证据:").append(source).append(" 表格").append(tableNo)
                                .append(" 第").append(rowNo).append("行]\n")
                                .append(String.join(" | ", cells)).append("\n");
                    }
                    rowNo++;
                }
                tableNo++;
            }
            return result.toString();
        }
    }

    private String extractZip(byte[] bytes, String source) throws IOException {
        StringBuilder result = new StringBuilder();
        int entries = 0;
        int expanded = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new BizException(422, "Zip内文件数量超过80个，无法安全检查");
                }
                byte[] entryBytes = readBounded(input, MAX_ZIP_EXPANDED_BYTES - expanded);
                expanded += entryBytes.length;
                if (expanded > MAX_ZIP_EXPANDED_BYTES) {
                    throw new BizException(422, "Zip解压后的内容超过30MB，无法安全检查");
                }
                String name = entry.getName().replace('\\', '/');
                String type = extension(name);
                String nestedSource = source + "/" + name;
                try {
                    if ("pdf".equals(type) && validPdf(entryBytes)) {
                        result.append(extractPdf(entryBytes, nestedSource));
                    } else if ("doc".equals(type) && startsWith(entryBytes, new byte[]{(byte) 0xD0, (byte) 0xCF})) {
                        result.append(extractDoc(entryBytes, nestedSource));
                    } else if ("docx".equals(type) && validDocx(entryBytes)) {
                        result.append(extractDocx(entryBytes, nestedSource));
                    } else if (ZIP_TEXT_TYPES.contains(type)) {
                        String text = new String(entryBytes, StandardCharsets.UTF_8).trim();
                        if (StringUtils.hasText(text)) {
                            result.append("[证据:").append(nestedSource).append("]\n").append(text).append("\n");
                        }
                    }
                } catch (Exception ignored) {
                    result.append("[证据:").append(nestedSource).append("] 文件存在但无法提取文字\n");
                }
            }
        }
        return result.toString();
    }

    private byte[] readBounded(ZipInputStream input, int remaining) throws IOException {
        if (remaining <= 0) {
            throw new BizException(422, "Zip解压后的内容超过安全限制");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > remaining) {
                throw new BizException(422, "Zip解压后的内容超过30MB，无法安全检查");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean validPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return document.getNumberOfPages() > 0 && !document.isEncrypted();
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean validDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getDocument() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean validZip(byte[] bytes) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            return input.getNextEntry() != null;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source == null || source.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index >= 0 && index + 1 < fileName.length()
                ? fileName.substring(index + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest(bytes)) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("系统不支持SHA-256", ex);
        }
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "文件无法读取";
    }

    public record EvidenceDocument(
            String fileName,
            String fileType,
            long fileSize,
            String checksum,
            String extractedText,
            boolean truncated,
            boolean readableText
    ) {
    }
}
