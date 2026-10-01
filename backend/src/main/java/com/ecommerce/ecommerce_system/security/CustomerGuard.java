package com.ecommerce.ecommerce_system.security;

import com.ecommerce.ecommerce_system.model.User;
import com.ecommerce.ecommerce_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Central authorization helper for customer-owned resources.
 *
 * The app trusts the signed-in JWT to establish identity; endpoints must never
 * rely on a client-supplied customerId alone. This guard resolves the caller's
 * own customer profile so controllers can reject cross-customer (IDOR) access.
 */
@Component
public class CustomerGuard {

    @Autowired
    private UserRepository userRepository;

    public boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** The customerId linked to the authenticated user, or null for admins. */
    public Long currentCustomerId(Authentication auth) {
        if (auth == null) {
            return null;
        }
        return userRepository.findByUsername(auth.getName())
                .map(User::getCustomerId)
                .orElse(null);
    }

    /** Admins may access any customer; a customer may only access their own data. */
    public boolean canAccess(Long customerId, Authentication auth) {
        if (isAdmin(auth)) {
            return true;
        }
        Long own = currentCustomerId(auth);
        return own != null && own.equals(customerId);
    }
}
