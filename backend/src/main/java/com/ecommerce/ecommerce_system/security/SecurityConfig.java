package com.ecommerce.ecommerce_system.security;

import com.ecommerce.ecommerce_system.repository.UserRepository;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/health", "/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // Error dispatch must not 403 to anonymous users
                        .requestMatchers("/error").permitAll()
                        // Public reads
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                        // Customer-owned writes (owner check in controllers)
                        .requestMatchers(HttpMethod.POST, "/api/orders").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/orders/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/orders/customer/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/**").authenticated()
                        .requestMatchers("/api/addresses/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/customers/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/customers/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/customer/**").authenticated()
                        // Receipts are the customer's own document, so any signed-in user may
                        // fetch one; CustomerGuard inside the controller enforces ownership.
                        // Must sit above the blanket /api/payments/** admin rule below.
                        .requestMatchers(HttpMethod.GET, "/api/payments/*/receipt").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/order/*").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/returns").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/returns/customer/**").authenticated()
                        // Staff: catalog work. Creating and editing products is a
                        // catalog task, but deletion is not - a staff account should
                        // never be able to remove the whole catalog.
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        // Staff read orders so they can answer "where is my order?".
                        // Read only - changing status, issuing refunds or cancelling
                        // stays admin. Sits above the blanket admin rule below, and
                        // below the customer-scoped rule so a customer still only
                        // ever reaches their own orders.
                        .requestMatchers(HttpMethod.GET, "/api/orders").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAnyRole("ADMIN", "STAFF")
                        // Admin only
                        .requestMatchers("/api/coupons/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/returns/**").hasRole("ADMIN")
                        .requestMatchers("/api/returns").hasRole("ADMIN")
                        .requestMatchers("/api/orders/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/customers").hasRole("ADMIN")
                        .requestMatchers("/api/payments").hasRole("ADMIN")
                        .requestMatchers("/api/payments/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Allowed browser origins.
     *
     * The deployed storefront is a separate origin from the API, so the browser
     * sends a preflight before every JSON request. If that origin is missing the
     * preflight is rejected and the browser reports a generic network error, which
     * looks like "the backend is down" even though it is answering fine.
     *
     * These are always allowed and cannot be switched off, because losing them
     * takes the whole storefront down with no visible cause. Extra origins come
     * from CORS_ALLOWED_ORIGINS.
     */
    private static final List<String> REQUIRED_ORIGINS = List.of(
            "https://ecommerce-store-shopease.vercel.app",
            "http://localhost:5173");

    /**
     * Reads extra origins from a comma-separated env var.
     *
     * Entries are trimmed and unquoted because the value is usually typed by hand
     * into a dashboard: a stray quote or a space after the comma otherwise becomes
     * part of the origin, and since no browser ever sends that exact string every
     * request is rejected while the log shows no clue why.
     */
    private static List<String> configuredOrigins() {
        String raw = System.getenv("CORS_ALLOWED_ORIGINS");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> origins = new ArrayList<>(REQUIRED_ORIGINS);
        for (String part : raw.split(",")) {
            String cleaned = part.trim().replaceAll("^[\"']|[\"']$", "");
            if (!cleaned.isBlank() && !origins.contains(cleaned)) {
                origins.add(cleaned);
            }
        }
        return origins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = configuredOrigins();
        System.out.println("[CORS] allowed origins: " + origins);
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
