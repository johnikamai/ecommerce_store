package com.ecommerce.order.controller;

import com.ecommerce.order.model.Customer;
import com.ecommerce.order.model.Product;
import com.ecommerce.order.model.Wishlist;
import com.ecommerce.order.repo.CustomerRepository;
import com.ecommerce.order.repo.ProductRepository;
import com.ecommerce.order.repo.WishlistRepository;
import com.ecommerce.order.security.CustomerGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistRepository wishlistRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CustomerGuard customerGuard;
    private final com.ecommerce.common.outbox.OutboxPublisher outbox;

    public WishlistController(WishlistRepository wishlistRepository,
                              CustomerRepository customerRepository,
                              ProductRepository productRepository,
                              CustomerGuard customerGuard,com.ecommerce.common.outbox.OutboxPublisher outbox) {
        this.outbox=outbox;
        this.wishlistRepository = wishlistRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.customerGuard = customerGuard;
    }

    private void changed(Long customerId,Long productId,boolean active){outbox.publish(java.util.UUID.randomUUID().toString(),"http://notification-service/internal/events","WISHLIST_CHANGED",Map.of("customerId",customerId,"productId",productId,"active",active,"version",System.currentTimeMillis()));}

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
        var saved=wishlistRepository.save(item);changed(customerId,productId,true);return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/customer/{customerId}/product/{productId}")
    public ResponseEntity<?> remove(@PathVariable Long customerId, @PathVariable Long productId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return wishlistRepository.findByCustomerIdAndProductId(customerId, productId)
                .map(item -> {
                    wishlistRepository.delete(item);changed(customerId,productId,false);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}