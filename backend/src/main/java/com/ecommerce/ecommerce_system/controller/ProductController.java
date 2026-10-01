package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import com.ecommerce.ecommerce_system.service.RestockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RestockService restockService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @GetMapping
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    /**
     * Storefront catalog: every product enriched with aggregate rating data
     * (averageRating, reviewCount) and unitsSold, so the frontend can sort by
     * popularity and rating without paging the backend.
     */
    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog() {
        Map<Long, double[]> reviewStats = new HashMap<>(); // pid -> [count, avg]
        for (Object[] row : reviewRepository.aggregateByProduct()) {
            Long pid = ((Number) row[0]).longValue();
            long cnt = ((Number) row[1]).longValue();
            double avg = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();
            reviewStats.put(pid, new double[]{cnt, Math.round(avg * 10.0) / 10.0});
        }

        Map<Long, Long> unitsSold = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldByProduct()) {
            Long pid = ((Number) row[0]).longValue();
            unitsSold.put(pid, ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : productRepository.findAll()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", p.getId());
            entry.put("name", p.getName());
            entry.put("description", p.getDescription());
            entry.put("category", p.getCategory());
            entry.put("price", p.getPrice());
            entry.put("stockQuantity", p.getStockQuantity());
            entry.put("sustainabilityScore", p.getSustainabilityScore());
            entry.put("sustainabilityLabel", p.getSustainabilityLabel());
            entry.put("imageUrl", p.getImageUrl());
            double[] stats = reviewStats.getOrDefault(p.getId(), new double[]{0, 0.0});
            entry.put("reviewCount", (long) stats[0]);
            entry.put("averageRating", stats[1]);
            entry.put("unitsSold", unitsSold.getOrDefault(p.getId(), 0L));
            result.add(entry);
        }
        return result;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Product product) {
        if (product.getSustainabilityScore() != null &&
                (product.getSustainabilityScore() < 0 || product.getSustainabilityScore() > 100)) {
            return ResponseEntity.badRequest().body("Sustainability score must be between 0 and 100");
        }
        Product saved = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product updated) {
        if (updated.getSustainabilityScore() != null &&
                (updated.getSustainabilityScore() < 0 || updated.getSustainabilityScore() > 100)) {
            return ResponseEntity.badRequest().body("Sustainability score must be between 0 and 100");
        }
        return productRepository.findById(id)
                .map(existing -> {
                    // Track the old stock level so we can detect a restock transition.
                    Integer oldStock = existing.getStockQuantity();

                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setCategory(updated.getCategory());
                    existing.setPrice(updated.getPrice());
                    existing.setStockQuantity(updated.getStockQuantity());
                    existing.setSustainabilityScore(updated.getSustainabilityScore());
                    existing.setImageUrl(updated.getImageUrl());

                    Product saved = productRepository.save(existing);

                    // If it was out of stock before and is in stock now, notify subscribers.
                    boolean wasOut = oldStock == null || oldStock <= 0;
                    boolean nowIn = saved.getStockQuantity() != null && saved.getStockQuantity() > 0;
                    if (wasOut && nowIn) {
                        restockService.checkRestock(saved);
                    }

                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}