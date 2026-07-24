package com.planning.platform.ai.service;

import com.planning.platform.ai.config.AiProperties;
import com.planning.platform.common.exception.BizException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class EvidenceDocumentServiceTest {

    private final AiProperties properties = new AiProperties();
    private final EvidenceDocumentService service = new EvidenceDocumentService(properties);

    @Test
    void extractsTraceableParagraphReferencesFromDocx() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("月计划审批功能已经完成并通过测试");
            document.write(output);
            bytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "成果报告.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        var result = service.inspect(file);

        assertThat(result.readableText()).isTrue();
        assertThat(result.extractedText()).contains("成果报告.docx 段落1", "月计划审批功能已经完成");
        assertThat(result.checksum()).hasSize(64);
    }

    @Test
    void rejectsFileWhoseExtensionDoesNotMatchContent() {
        MockMultipartFile file = new MockMultipartFile("file", "伪造.pdf", "application/pdf",
                "not-a-pdf".getBytes());

        BizException error = catchThrowableOfType(() -> service.inspect(file), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("文件类型不匹配");
    }
}
