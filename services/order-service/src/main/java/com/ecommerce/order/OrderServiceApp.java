package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages={"com.ecommerce.order.model","com.ecommerce.common.outbox"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages={"com.ecommerce.order.repo","com.ecommerce.common.outbox"})
@org.springframework.context.annotation.Import(com.ecommerce.common.outbox.OutboxConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApp.class, args);
    }
}

