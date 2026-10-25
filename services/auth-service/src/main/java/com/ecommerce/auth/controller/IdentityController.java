package com.ecommerce.auth.controller;

import com.ecommerce.auth.repo.*;
import com.ecommerce.auth.security.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Service-only identity contract. Passwords, hashes and credentials never leave auth. */
@RestController
@RequestMapping("/internal")
public class IdentityController {
    private final JwtUtil jwt;
    private final CustomUserDetailsService details;
    private final UserRepository users;
    private final CustomerRepository customers;
    public IdentityController(JwtUtil jwt,CustomUserDetailsService details,UserRepository users,CustomerRepository customers) {
        this.jwt=jwt;this.details=details;this.users=users;this.customers=customers;
    }
    @PostMapping("/auth/introspect")
    public ResponseEntity<?> introspect(@RequestBody Map<String,String> body) {
        try {
            String token=body.get("token");
            var principal=details.loadUserByUsername(jwt.extractUsername(token));
            if(!principal.isEnabled() || !principal.isAccountNonLocked() || !jwt.isTokenValid(token,principal.getUsername(),principal.getPassword())) return ResponseEntity.status(401).build();
            var user=users.findByUsername(principal.getUsername()).orElseThrow();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("username",user.getUsername());result.put("userId",user.getId());result.put("customerId",user.getCustomerId());
            result.put("role",user.getRole().name());
            result.put("authorities",principal.getAuthorities().stream().map(Object::toString).toList());
            return ResponseEntity.ok(result);
        } catch(Exception invalid) { return ResponseEntity.status(401).build(); }
    }
    @GetMapping("/customers")
    public Object customers(){return customers.findAll();}
    @GetMapping("/customers/{id}")
    public ResponseEntity<?> customer(@PathVariable Long id) {
        return customers.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
