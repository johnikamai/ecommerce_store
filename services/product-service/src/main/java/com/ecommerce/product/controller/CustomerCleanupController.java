package com.ecommerce.product.controller;
import com.ecommerce.product.repo.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
/** InternalServiceFilter requires the existing strong X-Service-Key. No Gateway route.
 * Only Auth's authorized admin deletion coordinator invokes this retryable operation. */
@RestController @RequestMapping("/internal/customers")
public class CustomerCleanupController {
    private final ReviewRepository reviews;private final RestockRequestRepository restocks;
    public CustomerCleanupController(ReviewRepository r,RestockRequestRepository s){reviews=r;restocks=s;}
    @PostMapping("/{id}/cleanup") @Transactional
    public Map<String,Boolean> cleanup(@PathVariable Long id){reviews.deleteByCustomerId(id);restocks.deleteByCustomerId(id);return Map.of("cleaned",true);}
}
