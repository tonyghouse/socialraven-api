package com.ghouse.socialraven.config;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    private ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    @Bean
    public ExecutorService virtualThreadExecutor() {
        return virtualThreads;
    }

    @PreDestroy
    public void shutdown() {
        virtualThreads.shutdownNow();
    }
}
