package com.kangban.config;

import com.kangban.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final AgentProperties agentProperties;

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("kangban-async-");
        executor.initialize();
        return executor;
    }

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, agentProperties.getCorePoolSize()));
        executor.setMaxPoolSize(Math.max(
                agentProperties.getCorePoolSize(), agentProperties.getMaxPoolSize()));
        executor.setQueueCapacity(Math.max(1, agentProperties.getQueueCapacity()));
        executor.setThreadNamePrefix("kangban-agent-");
        executor.initialize();
        return executor;
    }
}
