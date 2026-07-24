package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRedactionServiceTest {

    @Test
    void removesSensitiveFieldsAndMasksSensitiveText() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode source = mapper.createObjectNode();
        source.put("mobile", "13812345678");
        source.put("email", "employee@example.com");
        source.put("description", "联系人13812345678，邮箱employee@example.com，员工编号 E-001，身份证110101199001011234");

        String result = new AiRedactionService().redact(source).toString();

        assertThat(result).doesNotContain("13812345678", "employee@example.com", "E-001", "110101199001011234")
                .contains("***", "138****5678", "110101********1234");
        assertThat(new AiRedactionService().redactText("联系13812345678"))
                .isEqualTo("联系138****5678");
    }
}
