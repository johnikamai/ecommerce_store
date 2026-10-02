package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Inventory;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.InventoryRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * Reports warehouse stock derived from Product.stockQuantity, which is the
     * single source of truth for availability (it is what orders deduct from and
     * cancellations restore to).
     */
    private List<Map<String, Object>> viewOf(List<Product> products) {
        return products.stream().map(p -> {
            Map<String, Object> row = new HashMap<>();
            row.put("productId", p.getId());
            row.put("name", p.getName());
            row.put("category", p.getCategory());
            row.put("quantityAvailable", p.getStockQuantity());
            row.put("reorderLevel", p.getEffectiveReorderLevel());
            row.put("lowStock", p.isLowStock());
            row.put("outOfStock", p.isOutOfStock());
            return row;
        }).toList();
    }

    @GetMapping
    public List<Map<String, Object>> getAll() {
        return viewOf(productRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Inventory inventory) {
        Long productId = inventory.getProduct().getId();
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body("Product not found: " + productId);
        }
        inventory.setProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryRepository.save(inventory));
    }

    // GET /api/inventory/low-stock
    @GetMapping("/low-stock")
    public List<Map<String, Object>> getLowStock() {
        return viewOf(productRepository.findAll().stream().filter(Product::isLowStock).toList());
    }
}
