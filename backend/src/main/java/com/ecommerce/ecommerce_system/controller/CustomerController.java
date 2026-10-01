package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping
    public List<Customer> getAll() {
        return customerRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        if (!customerGuard.canAccess(id, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Customer customer) {
        if (customerRepository.findByEmail(customer.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already in use: " + customer.getEmail());
        }

        // Accept an optional referral code from the referrer. If present and valid,
        // link this new customer to that referrer (referredBy).
        String referrerCode = customer.getReferralCode();
        if (referrerCode != null && !referrerCode.isBlank()) {
            Customer referrer = customerRepository.findByReferralCode(referrerCode).orElse(null);
            if (referrer == null) {
                return ResponseEntity.badRequest().body("Invalid referral code: " + referrerCode);
            }
            customer.setReferredBy(referrer);
        }

        // Give this new customer their OWN unique referral code to share with friends.
        customer.setReferralCode(generateReferralCode(customer.getName()));
        customer.setReferralRewarded(false);

        Customer saved = customerRepository.save(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Build a unique referral code, e.g. "RAVI-A1B2C3".
    private String generateReferralCode(String name) {
        String base = (name == null || name.isBlank()) ? "USER" : name.trim().toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, Math.min(4, name.trim().length()));
        if (base.isEmpty()) {
            base = "USER";
        }
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String code = base + "-" + suffix;
        // Ensure uniqueness (extremely unlikely to collide, but be safe).
        while (customerRepository.findByReferralCode(code).isPresent()) {
            suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            code = base + "-" + suffix;
        }
        return code;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Customer updated, Authentication auth) {
        if (!customerGuard.canAccess(id, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return customerRepository.findById(id)
                .map(existing -> {
                    if (updated.getName() != null) {
                        existing.setName(updated.getName());
                    }
                    if (updated.getEmail() != null) {
                        // Uniqueness check before assigning a new email.
                        Customer other = customerRepository.findByEmail(updated.getEmail()).orElse(null);
                        if (other != null && !other.getId().equals(id)) {
                            return ResponseEntity.badRequest().body("Email already in use: " + updated.getEmail());
                        }
                        existing.setEmail(updated.getEmail());
                    }
                    if (updated.getPhone() != null) {
                        existing.setPhone(updated.getPhone());
                    }
                    if (updated.getShippingAddress() != null) {
                        existing.setShippingAddress(updated.getShippingAddress());
                    }
                    return ResponseEntity.ok(customerRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        if (!customerGuard.canAccess(id, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        if (!customerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        customerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}