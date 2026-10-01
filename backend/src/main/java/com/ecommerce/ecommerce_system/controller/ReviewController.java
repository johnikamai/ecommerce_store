package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.OrderStatus;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.Review;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.OrderItemRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping("/product/{productId}")
    public List<Review> getForProduct(@PathVariable Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    @GetMapping("/product/{productId}/summary")
    public Map<String, Object> getSummary(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        return Map.of(
                "averageRating", Math.round(average * 10.0) / 10.0,
                "reviewCount", reviews.size());
    }

    // Only customers who actually bought this product (in a non-cancelled order)
    // may review it — and only once per product.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Review review, Authentication auth) {
        if (review.getProduct() == null || review.getProduct().getId() == null) {
            return ResponseEntity.badRequest().body("Product is required");
        }
        if (review.getCustomer() == null || review.getCustomer().getId() == null) {
            return ResponseEntity.badRequest().body("Customer is required");
        }
        if (review.getRating() == null) {
            return ResponseEntity.badRequest().body("Rating is required");
        }

        Long productId = review.getProduct().getId();
        Long customerId = review.getCustomer().getId();

        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only review as yourself");
        }

        Product product = productRepository.findById(productId).orElse(null);
        Customer customer = customerRepository.findById(customerId).orElse(null);

        if (product == null) {
            return ResponseEntity.badRequest().body("Product not found: " + productId);
        }
        if (customer == null) {
            return ResponseEntity.badRequest().body("Customer not found: " + customerId);
        }
        if (review.getRating() < 1 || review.getRating() > 5) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
        }

        // Purchase gating: you must have ordered this product to review it.
        boolean purchased = orderItemRepository
                .existsByProductIdAndOrder_CustomerIdAndOrder_StatusNot(productId, customerId, OrderStatus.CANCELLED);
        if (!purchased) {
            return ResponseEntity.badRequest().body("You can only review a product you have purchased");
        }

        // One review per product per customer.
        if (!reviewRepository.findByCustomerIdAndProductId(customerId, productId).isEmpty()) {
            return ResponseEntity.badRequest().body("You already reviewed this product — edit your existing review instead");
        }

        review.setProduct(product);
        review.setCustomer(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewRepository.save(review));
    }

    // Customer edits their own review.
    // Body: { "customerId": ..., "rating": ..., "comment": ... }
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Review updated, Authentication auth) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        Long ownerId = review.getCustomer().getId();
        if (!customerGuard.canAccess(ownerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only edit your own review");
        }
        if (updated.getRating() != null && (updated.getRating() < 1 || updated.getRating() > 5)) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
        }
        if (updated.getRating() != null) {
            review.setRating(updated.getRating());
        }
        review.setComment(updated.getComment());
        return ResponseEntity.ok(reviewRepository.save(review));
    }

    // Customer deletes their own review (admins delete via /api/admin/reviews).
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        Long ownerId = review.getCustomer().getId();
        if (customerGuard.isAdmin(auth)) {
            reviewRepository.delete(review);
            return ResponseEntity.noContent().build();
        }
        if (body != null && body.get("customerId") != null) {
            Long supplied = Long.valueOf(body.get("customerId").toString());
            if (!customerGuard.canAccess(supplied, auth)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only delete your own review");
            }
            if (!supplied.equals(ownerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only delete your own review");
            }
        } else if (!customerGuard.canAccess(ownerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only delete your own review");
        }
        reviewRepository.delete(review);
        return ResponseEntity.noContent().build();
    }
}