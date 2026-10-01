package com.ecommerce.ecommerce_system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "name", "ShopEase API",
                "status", "OK",
                "documentation", "See /health for service status, /api/products for the catalog.",
                "version", "1.0.0"
        );
    }

    @GetMapping({"/health", "/api/health"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "OK",
                "service", "ecommerce-backend",
                "time", Instant.now().toString()
        );
    }
}