package com.ecommerce.auth.controller;

import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.*;
import com.ecommerce.auth.service.StaffService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final com.ecommerce.auth.repo.CustomerOrderLedgerRepository ledger;
    private final CustomerRepository customerRepository;

    public AdminController(UserRepository userRepository,
                           CustomerRepository customerRepository, com.ecommerce.auth.repo.CustomerOrderLedgerRepository ledger) {
        this.ledger=ledger;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            Customer customer = u.getCustomerId() != null
                    ? customerRepository.findById(u.getCustomerId()).orElse(null) : null;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("role", u.getRole());
            row.put("enabled", u.isEnabled());
            row.put("customerId", u.getCustomerId());
            row.put("customerName", customer != null ? customer.getName() : null);
            result.add(row);
        }
        return result;
    }

    @GetMapping("/customers")
    public List<Customer> customers() {
        return customerRepository.findAll();
    }

    @PutMapping("/users/{id}/role")
    @Transactional
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                        @RequestBody Map<String, String> body,
                                        Authentication auth) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        try {
            Role role = Role.valueOf(java.util.Objects.requireNonNullElse(body.get("role"),"").toUpperCase());
            if(user.getRole()==Role.ADMIN&&role!=Role.ADMIN&&auth.getName().equals(user.getUsername()))return ResponseEntity.badRequest().body("You cannot remove your own admin access");
            // Don't allow demoting the last admin
            if (user.getRole() == Role.ADMIN && role != Role.ADMIN
                    && userRepository.countByRole(Role.ADMIN) <= 1) {
                return ResponseEntity.badRequest().body("Cannot demote the last admin");
            }
            user.setRole(role);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("id", user.getId(), "role", role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown role: " + body.get("role"));
        }
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<?> enabled(@PathVariable Long id,@RequestBody Map<String,Boolean> body,Authentication auth){var user=userRepository.findById(id).orElse(null);if(user==null)return ResponseEntity.notFound().build();if(body.get("enabled")==null)return ResponseEntity.badRequest().body("enabled flag is required");if(auth.getName().equals(user.getUsername()))return ResponseEntity.badRequest().body("You cannot block your own account");user.setEnabled(body.get("enabled"));return ResponseEntity.ok(userRepository.save(user));}
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,Authentication auth) {
        var user=userRepository.findById(id).orElse(null);if(user==null)return ResponseEntity.notFound().build();
        if(user.getRole()==Role.ADMIN||auth.getName().equals(user.getUsername()))return ResponseEntity.badRequest().body("Cannot delete an admin or your own account");
        if(user.getCustomerId()!=null&&ledger.existsByCustomerId(user.getCustomerId()))return ResponseEntity.badRequest().body("Cannot delete: this customer has orders. Handle or archive them first.");
        if(user.getCustomerId()!=null&&customerRepository.findAll().stream().anyMatch(c->c.getReferredBy()!=null&&user.getCustomerId().equals(c.getReferredBy().getId())))return ResponseEntity.badRequest().body("Cannot delete: this customer has referred other customers.");
        userRepository.delete(user);return ResponseEntity.noContent().build();
    }
}
