package com.planning.platform.ai.service;

import com.planning.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCryptoServiceTest {

    private final AiCryptoService service = new AiCryptoService("test-ai-encryption-key-2026");

    @Test
    void encryptsWithoutEchoingSecretAndCanDecrypt() {
        String first = service.encrypt("sk-sensitive-value");
        String second = service.encrypt("sk-sensitive-value");

        assertThat(first).startsWith("v1:").doesNotContain("sk-sensitive-value");
        assertThat(second).isNotEqualTo(first);
        assertThat(service.decrypt(first)).isEqualTo("sk-sensitive-value");
        assertThat(service.decrypt(second)).isEqualTo("sk-sensitive-value");
    }

    @Test
    void rejectsUnknownCiphertextFormat() {
        assertThatThrownBy(() -> service.decrypt("plain-secret"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("格式无效");
    }
}
