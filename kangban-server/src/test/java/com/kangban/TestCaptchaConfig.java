package com.kangban;

import com.kangban.dto.response.CaptchaResponse;
import com.kangban.service.CaptchaService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestCaptchaConfig {

    @Bean
    @Primary
    public CaptchaService testCaptchaService() {
        return new CaptchaService(null) {
            @Override
            public CaptchaResponse issue(String clientAddress) {
                return new CaptchaResponse(
                        "test-captcha",
                        "data:image/png;base64,dGVzdA==",
                        120
                );
            }

            @Override
            public boolean verify(String captchaId, String answer) {
                return "test-captcha".equals(captchaId) && "ABCDE".equalsIgnoreCase(answer);
            }
        };
    }
}
