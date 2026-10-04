package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.security.CustomerGuard;
import com.ecommerce.ecommerce_system.service.RecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Recommendation endpoints, both backed by {@link RecommendationService}.
 *
 * Two audiences, one controller:
 *   anonymous  -> GET /api/products/{productId}/recommendations  (frequently bought together)
 *   signed-in  -> GET /api/recommendations/personalized        (from their own order history)
 *                 GET /api/recommendations/taste-profile
 *
 * The personalized routes resolve the customer from the JWT via CustomerGuard
 * rather than reading an id off the request, so one shopper cannot ask for
 * another's taste profile.
 */
@RestController
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private CustomerGuard customerGuard;

    // GET /api/products/1/recommendations?limit=4
    @GetMapping("/api/products/{productId}/recommendations")
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

    // GET /api/recommendations/personalized?limit=8
    @GetMapping("/api/recommendations/personalized")
    public ResponseEntity<?> personalized(@RequestParam(defaultValue = "8") int limit,
                                          Authentication auth) {
        Long customerId = customerGuard.currentCustomerId(auth);
        if (customerId == null) {
            // Admins and shoppers with no customer profile have no history to
            // personalise from; an empty rail is the honest answer.
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(recommendationService.personalizedFor(customerId, clamp(limit)));
    }

    // GET /api/recommendations/taste-profile
    @GetMapping("/api/recommendations/taste-profile")
    public ResponseEntity<?> tasteProfile(Authentication auth) {
        Long customerId = customerGuard.currentCustomerId(auth);
        if (customerId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(recommendationService.tasteProfile(customerId));
    }

    /** Caps the page size so one request cannot ask for the whole catalog. */
    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 24));
    }
}
