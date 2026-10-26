package com.ecommerce.product.controller;
import com.ecommerce.product.repo.*;
import com.ecommerce.product.model.Review;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController @RequestMapping("/api/admin/reviews")
public class AdminReviewController {
    private final ReviewRepository reviews;
    public AdminReviewController(ReviewRepository reviews){this.reviews=reviews;}
    @GetMapping public List<Review> list(){return reviews.findAll().stream().sorted(Comparator.comparing(Review::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())).reversed()).toList();}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id){if(!reviews.existsById(id))return ResponseEntity.notFound().build();reviews.deleteById(id);return ResponseEntity.noContent().build();}
}
