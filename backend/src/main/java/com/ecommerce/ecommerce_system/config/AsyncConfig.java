package com.ecommerce.ecommerce_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Outbound email is dispatched on a small background pool.
 *
 * MailService talks to Brevo over HTTPS with a 20s timeout. Doing that inline
 * meant a single order placement blocked on two email round-trips, and creating
 * a coupon blocked on one per customer - long enough to blow the client's
 * request timeout. Email must never sit in the request path.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bounded pool with a short queue. If the queue is full the task is rejected
     * rather than queued forever, so a Brevo outage cannot exhaust memory; the
     * in-app notification is already persisted by then regardless.
     */
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}