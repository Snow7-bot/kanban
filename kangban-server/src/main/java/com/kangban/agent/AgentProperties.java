package com.kangban.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 内置 Agent 的运行边界。密钥只从环境变量绑定，不允许写入代码或日志。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    private boolean enabled = true;
    private int contextTtlSeconds = 300;
    private int maxIterations = 5;
    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 20;
    private String contextSecret = "";
}
