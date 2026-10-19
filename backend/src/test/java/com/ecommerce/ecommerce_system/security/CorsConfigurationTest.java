package com.ecommerce.ecommerce_system.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigurationTest {

    @Test
    void allowsViteFallbackPortsWithoutAllowingOtherOrigins() {
        var source = new SecurityConfig(null, null, null).corsConfigurationSource();
        var request = new MockHttpServletRequest("GET", "/api/products/catalog");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertEquals("http://localhost:5174", configuration.checkOrigin("http://localhost:5174"));
        assertNull(configuration.checkOrigin("https://untrusted.example"));
    }
}