package com.kangban;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSafetyTest {

    @Test
    void defaultsDoNotEnableRealAiOrSmsVerification() throws Exception {
        String application = resource("application.yml");
        String development = resource("application-dev.yml");
        String local = resource("application-local.yml");
        String compose = resource("docker-compose.yml");

        assertTrue(application.contains("provider: ${APP_AI_PROVIDER:mock}"));
        assertTrue(application.contains("default: local"));
        assertFalse(application.contains("active: dev"));
        assertFalse(development.contains("dev-code"));
        assertFalse(local.contains("dev-code"));
        assertTrue(local.contains("KANGBAN_CAPTCHA_SECRET"));
        assertTrue(compose.contains("SPRING_PROFILES_ACTIVE: prod"));
    }

    private String resource(String name) throws Exception {
        var classLoader = Thread.currentThread().getContextClassLoader();
        var url = classLoader.getResource(name);
        if (url != null) {
            try (var input = url.openStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return java.nio.file.Files.readString(java.nio.file.Path.of(name));
    }
}
