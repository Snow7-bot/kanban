package com.kangban.service;

import com.kangban.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CaptchaService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        valueOperations = operations;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service = new CaptchaService(redisTemplate);
        ReflectionTestUtils.setField(service, "secret", "test-only-captcha-secret");
    }

    @Test
    void issueReturnsPngDataAndStoresOnlyDigest() {
        var response = service.issue("127.0.0.1");

        assertThat(response.captchaId()).isNotBlank();
        assertThat(response.imageData()).startsWith("data:image/png;base64,");
        assertThat(response.expiresInSeconds()).isEqualTo(120);
        verify(valueOperations).set(
                anyString(),
                org.mockito.ArgumentMatchers.argThat(value -> value != null && !value.matches("[A-Z0-9]{5}")),
                any(Duration.class)
        );
    }

    @Test
    void verifyAcceptsOnlyAtomicSuccessResult() {
        when(redisTemplate.execute(any(), any(), anyString(), anyString())).thenReturn(1L);

        assertThat(service.verify("captcha-id", "abcde")).isTrue();

        when(redisTemplate.execute(any(), any(), anyString(), anyString())).thenReturn(-1L);
        assertThat(service.verify("captcha-id", "wrong")).isFalse();
    }

    @Test
    void issueIsRateLimitedByClientAddress() {
        when(valueOperations.increment(anyString())).thenReturn(11L);

        assertThatThrownBy(() -> service.issue("127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求过于频繁");
    }
}
