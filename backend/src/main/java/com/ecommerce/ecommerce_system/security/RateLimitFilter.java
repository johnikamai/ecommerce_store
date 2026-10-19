package com.ecommerce.ecommerce_system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory rate limiter for sensitive authentication endpoints
 * (login, registration, password reset, staff applications). Keyed by client IP
 * and path. Intentionally simple: a single-node deployment does not need a
 * distributed store, and the goal is to blunt brute-force / spam attempts.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!isLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + " " + request.getRequestURI();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        long now = System.currentTimeMillis();

        int count;
        synchronized (window) {
            if (now - window.start >= WINDOW_MILLIS) {
                window.start = now;
                window.count.set(0);
            }
            count = window.count.incrementAndGet();
        }

        if (count > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please slow down and try again shortly.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLimited(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/auth/resend-otp")
                || path.equals("/api/auth/verify-otp")
                || path.equals("/api/staff/apply");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private long start = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);
    }
}
