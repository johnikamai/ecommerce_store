package com.ecommerce.ecommerce_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcommerceSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommerceSystemApplication.class, args);
	}

}
