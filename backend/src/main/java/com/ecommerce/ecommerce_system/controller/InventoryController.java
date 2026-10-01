package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Inventory;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.InventoryRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public List<Inventory> getAll() {
        return inventoryRepository.findAll();
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
    public List<Inventory> getLowStock() {
        return inventoryRepository.findAll().stream()
                .filter(Inventory::isLowStock)
                .toList();
    }
}
