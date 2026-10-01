package com.ecommerce.ecommerce_system.config;

import com.ecommerce.ecommerce_system.model.Role;
import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a bootstrap admin on first start when ADMIN_USERNAME /
 * ADMIN_PASSWORD env vars are set. Idempotent: never overwrites an
 * existing username. Without these env vars it does nothing, so local
 * development is unaffected.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String username = System.getenv().getOrDefault("ADMIN_USERNAME", "");
        String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "");
        String email = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@shopease.com");
        if (username.isBlank() || password.isBlank() || password.length() < 8) {
            return;
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        System.out.println("[AdminSeeder] Bootstrap admin '" + username + "' created.");
    }
}