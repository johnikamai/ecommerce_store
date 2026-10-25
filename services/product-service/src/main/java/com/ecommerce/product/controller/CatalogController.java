package com.ecommerce.product.controller;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repo.ProductRepository;
import com.ecommerce.product.repo.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Public catalog reads. Writes and aggregate workflows remain in compatibility. */
@RestController
@Transactional(readOnly = true)
public class CatalogController {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogController(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @GetMapping("/api/products")
    public List<Product> products() { return products.findAll(); }

    @GetMapping("/api/products/{id:[0-9]+}")
    public ResponseEntity<Product> product(@PathVariable Long id) {
        return products.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/categories")
    public List<Map<String, Object>> categories() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : products.countByCategory()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var category : categories.findAll()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", category.getId());
            row.put("name", category.getName());
            row.put("parentId", category.getParentId());
            row.put("productCount", counts.getOrDefault(category.getName(), 0L));
            result.add(row);
        }
        result.sort(Comparator.comparing(row -> (String) row.get("name")));
        return result;
    }
}
