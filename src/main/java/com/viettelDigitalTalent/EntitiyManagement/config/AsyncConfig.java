package com.viettelDigitalTalent.EntitiyManagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(processors); // Số thread duy trì dựa trên CPU core
        executor.setMaxPoolSize(processors * 2); // Số thread tối đa
        executor.setThreadNamePrefix("SOC-Async-");
        executor.initialize();
        return executor;
    }
}