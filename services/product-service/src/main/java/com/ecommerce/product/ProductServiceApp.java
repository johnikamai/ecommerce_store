package com.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages={"com.ecommerce.product.model","com.ecommerce.common.outbox"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages={"com.ecommerce.product.repo","com.ecommerce.common.outbox"})
@org.springframework.context.annotation.Import(com.ecommerce.common.outbox.OutboxConfiguration.class)
public class ProductServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApp.class, args);
    }
}
