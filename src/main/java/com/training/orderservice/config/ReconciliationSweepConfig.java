package com.training.orderservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReconciliationSweepProperties.class)
public class ReconciliationSweepConfig {

    // Dedicated pool so the sweep never competes with the notification-executor
    // (AsyncConfig) or request-handling threads. Spring Boot auto-wires @Scheduled
    // methods to this bean since it's the only TaskScheduler in the context.
    @Bean(name = "reconciliation-scheduler")
    public ThreadPoolTaskScheduler reconciliationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reconciliation-sweep-");
        scheduler.initialize();
        return scheduler;
    }
}