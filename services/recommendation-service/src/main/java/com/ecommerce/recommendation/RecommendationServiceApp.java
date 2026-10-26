package com.ecommerce.recommendation;
@org.springframework.boot.autoconfigure.SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class RecommendationServiceApp {public static void main(String[] args){org.springframework.boot.SpringApplication.run(RecommendationServiceApp.class,args);}}