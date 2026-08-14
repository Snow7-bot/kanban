package com.kangban.common;

import com.kangban.rag.RagUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRagUnavailableToServiceUnavailable() {
        var response = new GlobalExceptionHandler()
                .handleRagUnavailable(new RagUnavailableException("中文向量服务暂时不可用，请稍后重试。"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("中文向量服务暂时不可用，请稍后重试。");
    }
}
