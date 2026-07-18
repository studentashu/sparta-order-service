package com.training.orderservice.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notification-executor")
    public TaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-exec-");
        // Carries the request thread's MDC (correlation ID, Section 8) onto the pooled
        // thread the @Async call actually runs on, and restores the pool thread's own
        // MDC afterward so nothing leaks into the next task it picks up.
        executor.setTaskDecorator(runnable -> {
            Map<String, String> callerMdc = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previousMdc = MDC.getCopyOfContextMap();
                try {
                    if (callerMdc != null) {
                        MDC.setContextMap(callerMdc);
                    }
                    runnable.run();
                } finally {
                    if (previousMdc != null) {
                        MDC.setContextMap(previousMdc);
                    } else {
                        MDC.clear();
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
