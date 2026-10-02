package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Coupon;
import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.repository.CouponRepository;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public List<Coupon> getAll() {
        return couponRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Coupon coupon) {
        if (coupon.getCode() == null || coupon.getCode().isBlank()) {
            return ResponseEntity.badRequest().body("Coupon code is required");
        }
        String code = coupon.getCode().trim().toUpperCase();
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            return ResponseEntity.badRequest().body("Coupon code already exists: " + code);
        }
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Discount value must be greater than zero");
        }
        if (coupon.getDiscountType() == com.ecommerce.ecommerce_system.model.CouponType.PERCENT
                && coupon.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            return ResponseEntity.badRequest().body("Percent discount cannot exceed 100");
        }
        coupon.setCode(code);
        Coupon saved = couponRepository.save(coupon);

        // Announce the new offer to every customer (in-app + email).
        String description = (saved.getDescription() == null || saved.getDescription().isBlank())
                ? "Get " + saved.getDiscountValue() + (saved.getDiscountType() == com.ecommerce.ecommerce_system.model.CouponType.PERCENT ? "% off" : " off")
                : saved.getDescription();
        // Fan-out runs in the background: with many customers this would otherwise
        // keep the admin request open for minutes. The coupon itself is already
        // saved, so returning now is safe.
        notificationService.broadcastOffer(saved.getCode(), description, customerRepository.findAll());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Coupon updated) {
        return couponRepository.findById(id)
                .map(coupon -> {
                    if (updated.getDiscountValue() == null
                            || updated.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                        return ResponseEntity.badRequest().body("Discount value must be greater than zero");
                    }
                    if (updated.getDiscountType() == com.ecommerce.ecommerce_system.model.CouponType.PERCENT
                            && updated.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                        return ResponseEntity.badRequest().body("Percent discount cannot exceed 100");
                    }
                    coupon.setDescription(updated.getDescription());
                    coupon.setDiscountType(updated.getDiscountType());
                    coupon.setDiscountValue(updated.getDiscountValue());
                    coupon.setMinimumOrderAmount(updated.getMinimumOrderAmount());
                    coupon.setExpiryDate(updated.getExpiryDate());
                    coupon.setMaxUses(updated.getMaxUses());
                    return ResponseEntity.ok(couponRepository.save(coupon));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/coupons/{id}/status  { "active": true|false }
    @PutMapping("/{id}/status")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return couponRepository.findById(id)
                .map(coupon -> {
                    Boolean active = body.get("active");
                    if (active == null) {
                        return ResponseEntity.badRequest().body("active flag is required");
                    }
                    coupon.setActive(active);
                    return ResponseEntity.ok(couponRepository.save(coupon));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!couponRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        couponRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}