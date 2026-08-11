package com.kangban.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentContextSignerTest {

    @Test
    void signsAndVerifiesShortLivedServerContext() {
        AgentProperties properties = new AgentProperties();
        properties.setContextSecret("test-agent-secret");
        AgentContextSigner signer = new AgentContextSigner(new ObjectMapper(), properties);
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext source = new AgentExecutionContext(
                9L, 15L, 2L, 31L, "run-1", "trace-1", now - 1, now + 200L);

        AgentExecutionContext verified = signer.verify(signer.sign(source));

        assertThat(verified).isEqualTo(source);
        assertThat(verified.scopeKey()).isEqualTo("15:2");
    }

    @Test
    void rejectsTamperedAndExpiredContext() {
        AgentProperties properties = new AgentProperties();
        properties.setContextSecret("test-agent-secret");
        AgentContextSigner signer = new AgentContextSigner(new ObjectMapper(), properties);
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext expired = new AgentExecutionContext(
                9L, 9L, null, 31L, "run-1", "trace-1", now - 2, now - 1);

        String token = signer.sign(expired);
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertThatThrownBy(() -> signer.verify(tampered))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agent 上下文已过期");
    }
}
