package com.kangban.rag;

import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagAdminGuardTest {

    @Test
    void requiresConfiguredAdminToken() {
        RagProperties properties = new RagProperties();
        properties.setAdminToken("admin-token-for-test");
        RagAdminGuard guard = new RagAdminGuard(properties);

        guard.require("admin-token-for-test");
        assertThatThrownBy(() -> guard.require("wrong-token"))
                .hasMessage("知识库管理凭据无效");
    }
}
