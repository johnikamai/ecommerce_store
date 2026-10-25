package com.ecommerce.notification;
@org.springframework.boot.autoconfigure.SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class NotificationServiceApp {public static void main(String[] args){org.springframework.boot.SpringApplication.run(NotificationServiceApp.class,args);}}
