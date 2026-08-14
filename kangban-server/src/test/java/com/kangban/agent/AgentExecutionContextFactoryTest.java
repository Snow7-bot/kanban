package com.kangban.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionContextFactoryTest {

    @Test
    void createsScopedContextWithoutAcceptingClientIdentity() {
        AgentProperties properties = new AgentProperties();
        properties.setContextTtlSeconds(60);
        AgentExecutionContext context = new AgentExecutionContextFactory(properties)
                .create(9L, 15L, 2L, 31L);

        assertThat(context.actorUserId()).isEqualTo(9L);
        assertThat(context.subjectUserId()).isEqualTo(15L);
        assertThat(context.memberId()).isEqualTo(2L);
        assertThat(context.sessionId()).isEqualTo(31L);
        assertThat(context.expiresAtEpochSecond() - context.issuedAtEpochSecond())
                .isEqualTo(60L);
        assertThat(context.runId()).isNotBlank();
    }
}
