package com.planning.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationFailureUsesHttp422() {
        var response = handler.handleValidationException(
                new MissingServletRequestParameterException("monthPlanId", "Long")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(422);
    }

    @Test
    void disabledLegacyWriteUsesHttp405() {
        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(405);
    }

    @Test
    void oversizedUploadUsesHttp413() {
        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(20L * 1024 * 1024));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(413);
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalMessage() {
        var response = handler.handleException(new IllegalStateException("internal-detail"));

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("服务器内部错误");
    }
}
