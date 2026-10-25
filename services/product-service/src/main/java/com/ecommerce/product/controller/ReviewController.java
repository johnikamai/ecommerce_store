package com.ecommerce.product.controller;

import com.ecommerce.product.model.Customer;
import com.ecommerce.product.model.OrderStatus;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.Review;
import com.ecommerce.product.repo.CustomerRepository;
import com.ecommerce.product.repo.OrderItemRepository;
import com.ecommerce.product.repo.ProductRepository;
import com.ecommerce.product.repo.ReviewRepository;
import com.ecommerce.product.security.CustomerGuard;
import com.ecommerce.product.service.ReviewSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerGuard customerGuard;
    private final ReviewSummaryService reviewSummaryService;

    public ReviewController(ReviewRepository reviewRepository,
                            ProductRepository productRepository,
                            CustomerRepository customerRepository,
                            OrderItemRepository orderItemRepository,
                            CustomerGuard customerGuard,
                            ReviewSummaryService reviewSummaryService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerGuard = customerGuard;
        this.reviewSummaryService = reviewSummaryService;
    }

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

    /**
     * Pros/cons digest built from the written reviews. Deliberately separate
     * from {@link #getSummary} so the star-rating widget keeps its tiny payload.
     */
    @GetMapping("/product/{productId}/ai-summary")
    public ReviewSummaryService.Summary getAiSummary(@PathVariable Long productId) {
        return reviewSummaryService.summarize(reviewRepository.findByProductId(productId));
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
