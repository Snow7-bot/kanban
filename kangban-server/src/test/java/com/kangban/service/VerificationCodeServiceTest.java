package com.kangban.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceTest {

    @Test
    void devProfileStoresConfiguredDevelopmentCode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Environment environment = mock(Environment.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        VerificationCodeService service = new VerificationCodeService(redisTemplate);
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "devCode", "000000");

        service.issue("register", "13900000000");

        verify(valueOperations).set(
                "kangban:verification:register:13900000000",
                "000000",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void nonDevProfileDoesNotEnableDevelopmentCode() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        VerificationCodeService service = new VerificationCodeService(mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "devCode", "000000");

        assertFalse(service.isDevCodeEnabled());
    }
}
