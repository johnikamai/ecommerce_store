package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.Wishlist;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.WishlistRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(wishlistRepository.findByCustomerId(customerId));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Long> body, Authentication auth) {
        Long customerId = body.get("customerId");
        Long productId = body.get("productId");

        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        if (wishlistRepository.findByCustomerIdAndProductId(customerId, productId).isPresent()) {
            return ResponseEntity.badRequest().body("Already in wishlist");
        }

        Customer customer = customerRepository.findById(customerId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);

        if (customer == null || product == null) {
            return ResponseEntity.badRequest().body("Customer or product not found");
        }

        Wishlist item = new Wishlist();
        item.setCustomer(customer);
        item.setProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(wishlistRepository.save(item));
    }

    @DeleteMapping("/customer/{customerId}/product/{productId}")
    public ResponseEntity<?> remove(@PathVariable Long customerId, @PathVariable Long productId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return wishlistRepository.findByCustomerIdAndProductId(customerId, productId)
                .map(item -> {
                    wishlistRepository.delete(item);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}