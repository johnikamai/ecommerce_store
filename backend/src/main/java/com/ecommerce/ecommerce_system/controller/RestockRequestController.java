package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.RestockRequest;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.RestockRequestRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restock-requests")
public class RestockRequestController {

    @Autowired
    private RestockRequestRepository restockRequestRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerGuard customerGuard;

    // Subscribe: "notify me when this product is back in stock"
    @PostMapping
    public ResponseEntity<?> subscribe(@RequestBody RestockRequest request, Authentication auth) {
        if (request.getCustomer() == null || request.getCustomer().getId() == null) {
            return ResponseEntity.badRequest().body("Customer is required");
        }
        Long customerId = request.getCustomer().getId();
        Long productId = request.getProduct().getId();

        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        Customer customer = customerRepository.findById(customerId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (customer == null) {
            return ResponseEntity.badRequest().body("Customer not found: " + customerId);
        }
        if (product == null) {
            return ResponseEntity.badRequest().body("Product not found: " + productId);
        }
        // Don't create a duplicate subscription for the same customer+product.
        if (restockRequestRepository.existsByCustomerIdAndProductId(customerId, productId)) {
            return ResponseEntity.badRequest().body("Already subscribed to this product");
        }

        request.setCustomer(customer);
        request.setProduct(product);
        request.setNotified(false);

        return ResponseEntity.status(HttpStatus.CREATED).body(restockRequestRepository.save(request));
    }

    // List a customer's active "notify me" subscriptions
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(restockRequestRepository.findByCustomerId(customerId));
    }

    // Unsubscribe
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        RestockRequest existing = restockRequestRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!customerGuard.canAccess(existing.getCustomer().getId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        restockRequestRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
