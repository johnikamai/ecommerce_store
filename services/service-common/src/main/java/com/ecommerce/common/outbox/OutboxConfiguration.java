package com.ecommerce.common.outbox;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling @Import({OutboxPublisher.class,OutboxWorker.class})
public class OutboxConfiguration {}
