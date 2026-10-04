package com.ecommerce.ecommerce_system.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JwtRegressionTest {
    @Test void passwordChangeInvalidatesPreviouslyIssuedToken() {
        JwtUtil jwt = new JwtUtil("test-only-secret-that-is-at-least-32-bytes");
        String token = jwt.generateToken("ruby", "CUSTOMER", "old-password-hash");
        assertTrue(jwt.isTokenValid(token, "ruby", "old-password-hash"));
        assertFalse(jwt.isTokenValid(token, "ruby", "new-password-hash"));
        assertFalse(jwt.isTokenValid(token, "someone-else", "old-password-hash"));
    }
    @Test void weakConfiguredSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short"));
    }
}
