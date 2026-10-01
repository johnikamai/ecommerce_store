package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Category;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.CategoryRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    // GET /api/categories -> list with product counts, parents first.
    @GetMapping
    public List<Map<String, Object>> getAll() {
        List<Category> all = categoryRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category c : all) {
            long productCount = productRepository.findAll().stream()
                    .filter(p -> c.getName().equals(p.getCategory()))
                    .count();
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("parentId", c.getParentId());
            row.put("productCount", productCount);
            result.add(row);
        }
        result.sort(Comparator.comparing(m -> (String) m.get("name")));
        return result;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Category name is required");
        }
        String name = category.getName().trim();
        if (categoryRepository.existsByName(name)) {
            return ResponseEntity.badRequest().body("Category already exists: " + name);
        }
        if (category.getParentId() != null && categoryRepository.findById(category.getParentId()).isEmpty()) {
            return ResponseEntity.badRequest().body("Parent category not found: " + category.getParentId());
        }
        category.setName(name);
        Category saved = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // PUT /api/categories/{id} { name, parentId } — rename also updates matching products,
    // so product.category strings and the category table never drift apart.
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Category updated) {
        return categoryRepository.findById(id)
                .map(category -> {
                    if (updated.getName() != null && !updated.getName().isBlank()) {
                        String newName = updated.getName().trim();
                        if (!newName.equals(category.getName()) && categoryRepository.existsByName(newName)) {
                            return ResponseEntity.badRequest().body("Category already exists: " + newName);
                        }
                        String oldName = category.getName();
                        category.setName(newName);
                        for (Product p : productRepository.findAll()) {
                            if (oldName.equals(p.getCategory())) {
                                p.setCategory(newName);
                                productRepository.save(p);
                            }
                        }
                    }
                    if (updated.getParentId() != null && categoryRepository.findById(updated.getParentId()).isEmpty()) {
                        return ResponseEntity.badRequest().body("Parent category not found: " + updated.getParentId());
                    }
                    category.setParentId(updated.getParentId());
                    return ResponseEntity.ok(categoryRepository.save(category));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/categories/{id} — blocked while products use this category;
    // subcategories are detached (parentId -> null) instead of being deleted.
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(category -> {
                    long productCount = productRepository.findAll().stream()
                            .filter(p -> category.getName().equals(p.getCategory()))
                            .count();
                    if (productCount > 0) {
                        return ResponseEntity.badRequest()
                                .body("Cannot delete: " + productCount + " product(s) are in this category. Rename or reassign them first.");
                    }
                    for (Category child : categoryRepository.findAll()) {
                        if (id.equals(child.getParentId())) {
                            child.setParentId(null);
                            categoryRepository.save(child);
                        }
                    }
                    categoryRepository.delete(category);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}