package org.blr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutionConfig {

    @Bean(name = "graphBuildExecutor")
    public ThreadPoolTaskExecutor graphBuildExecutor(GraphIngestionProperties properties) {
        int poolSize = Math.max(1, properties.build().maxConcurrentBuilds());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("graph-build-");
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
