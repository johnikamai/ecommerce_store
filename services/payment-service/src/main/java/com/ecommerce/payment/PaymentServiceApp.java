package com.ecommerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages={"com.ecommerce.payment.model","com.ecommerce.common.outbox"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages={"com.ecommerce.payment.repo","com.ecommerce.common.outbox"})
@org.springframework.context.annotation.Import(com.ecommerce.common.outbox.OutboxConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients
public class PaymentServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApp.class, args);
    }
}
