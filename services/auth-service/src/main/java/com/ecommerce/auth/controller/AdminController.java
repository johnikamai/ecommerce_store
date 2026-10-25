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
    private final CustomerRepository customerRepository;

    public AdminController(UserRepository userRepository,
                           CustomerRepository customerRepository) {
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
            Role role = Role.valueOf(body.get("role").toUpperCase());
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

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}