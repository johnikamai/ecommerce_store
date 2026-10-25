package com.ecommerce.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping({"/health", "/api/health"})
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "auth-service");
    }
}
