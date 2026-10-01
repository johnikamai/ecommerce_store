package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.service.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    // GET /api/products/1/recommendations?limit=4
    @GetMapping
    public ResponseEntity<?> getFrequentlyBoughtTogether(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "4") int limit) {
        try {
            List<Map<String, Object>> recommendations =
                    recommendationService.frequentlyBoughtTogether(productId, limit);
            return ResponseEntity.ok(recommendations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
