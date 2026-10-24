package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Coupon;
import com.ecommerce.ecommerce_system.model.CouponType;
import com.ecommerce.ecommerce_system.repository.CouponRepository;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponRepository couponRepository;
    private final CustomerRepository customerRepository;
    private final NotificationService notificationService;

    public CouponController(CouponRepository couponRepository,
                            CustomerRepository customerRepository,
                            NotificationService notificationService) {
        this.couponRepository = couponRepository;
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
    }

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

    /**
     * GET /api/coupons/recommend?subtotal=1500
     *
     * Scans every active, redeemable coupon and returns the one that yields
     * the largest saving for the given cart subtotal. Returns 204 when no
     * coupon is applicable (minimum order not met, all expired / exhausted).
     *
     * The endpoint is intentionally public: showing the best discount before
     * sign-in is a conversion nudge, not a security risk. No coupon is marked
     * used here - that happens at order placement.
     */
    @GetMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestParam BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("subtotal must be positive");
        }

        Coupon best = null;
        BigDecimal bestSaving = BigDecimal.ZERO;

        for (Coupon c : couponRepository.findAll()) {
            if (!c.isRedeemable()) continue;
            if (c.getMinimumOrderAmount() != null
                    && subtotal.compareTo(c.getMinimumOrderAmount()) < 0) continue;

            BigDecimal saving;
            if (c.getDiscountType() == CouponType.PERCENT) {
                saving = subtotal
                        .multiply(c.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                saving = c.getDiscountValue().min(subtotal);
            }

            if (saving.compareTo(bestSaving) > 0) {
                bestSaving = saving;
                best = c;
            }
        }

        if (best == null) {
            return ResponseEntity.noContent().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code",          best.getCode());
        result.put("description",   best.getDescription());
        result.put("discountType",  best.getDiscountType());
        result.put("discountValue", best.getDiscountValue());
        result.put("minimumOrderAmount", best.getMinimumOrderAmount());
        result.put("saving",        bestSaving);
        return ResponseEntity.ok(result);
    }
}