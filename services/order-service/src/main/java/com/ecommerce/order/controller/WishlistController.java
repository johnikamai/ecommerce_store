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

    private void changed(Wishlist item, boolean active) {
        long version = Math.max(System.currentTimeMillis() * 1000, item.getAlertVersion() + 1);
        item.setAlertVersion(version);
        var payload = new java.util.LinkedHashMap<String,Object>();
        payload.put("customerId",item.getCustomerId()); payload.put("productId",item.getProductId());
        payload.put("active",active); payload.put("version",version);
        payload.put("targetPrice",item.getTargetPrice());
        payload.put("notifyOnDrop",item.isNotifyOnDrop()); payload.put("notifyOnRestock",item.isNotifyOnRestock());
        outbox.publish(java.util.UUID.randomUUID().toString(),"http://notification-service/internal/events","WISHLIST_CHANGED",payload);
    }

    public record Preferences(java.math.BigDecimal targetPrice, Boolean notifyOnDrop, Boolean notifyOnRestock) {}
    @PutMapping("/customer/{customerId}/product/{productId}/preferences")
    public ResponseEntity<?> preferences(@PathVariable Long customerId, @PathVariable Long productId,
                                          @RequestBody Preferences body, Authentication auth) {
        if (!customerGuard.canAccess(customerId,auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        if (body.targetPrice()!=null && (body.targetPrice().signum()<=0 || body.targetPrice().scale()>2 || body.targetPrice().compareTo(new java.math.BigDecimal("9999999999.99"))>0))
            return ResponseEntity.badRequest().body("Target price must be positive with at most two decimal places");
        var item=wishlistRepository.findByCustomerIdAndProductId(customerId,productId).orElse(null);
        if(item==null)return ResponseEntity.notFound().build();
        item.setTargetPrice(body.targetPrice());
        if(body.notifyOnDrop()!=null)item.setNotifyOnDrop(body.notifyOnDrop());
        if(body.notifyOnRestock()!=null)item.setNotifyOnRestock(body.notifyOnRestock());
        changed(item,true);
        return ResponseEntity.ok(wishlistRepository.save(item));
    }

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
        item.setInitialPrice(product.getPrice());
        changed(item,true);
        return ResponseEntity.status(HttpStatus.CREATED).body(wishlistRepository.save(item));
    }

    @DeleteMapping("/customer/{customerId}/product/{productId}")
    public ResponseEntity<?> remove(@PathVariable Long customerId, @PathVariable Long productId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return wishlistRepository.findByCustomerIdAndProductId(customerId, productId)
                .map(item -> {
                    changed(item,false);wishlistRepository.delete(item);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
