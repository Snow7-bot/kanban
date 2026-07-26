package com.kangban;

import com.kangban.service.VerificationCodeService;
import com.kangban.service.SmsSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration provides a verification service that bypasses Redis.
 */
@TestConfiguration
public class TestVerificationCodeConfig {

    @Bean
    @Primary
    public VerificationCodeService testVerificationCodeService() {
        return new VerificationCodeService(null) {
            @Override
            public String issue(String purpose, String phone) {
                return "888888";
            }

            @Override
            public boolean verify(String purpose, String phone, String code) {
                return "888888".equals(code);
            }
        };
    }

    @Bean
    @Primary
    public SmsSender smsSender() {
        return (phone, code) -> { };
    }
}
