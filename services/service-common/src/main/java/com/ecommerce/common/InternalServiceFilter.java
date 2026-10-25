package com.ecommerce.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;


public class InternalServiceFilter extends OncePerRequestFilter {
    private final byte[] key;
    public InternalServiceFilter(@Value("${services.internal-key}") String configured) {
        if(configured.length()<32) throw new IllegalArgumentException("services.internal-key must be at least 32 characters");
        key=configured.getBytes(StandardCharsets.UTF_8);
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().startsWith("/internal/")) {
            String supplied=request.getHeader("X-Service-Key");
            if(supplied==null || !MessageDigest.isEqual(key,supplied.getBytes(StandardCharsets.UTF_8))) { response.sendError(401);return; }
        }
        chain.doFilter(request,response);
    }
}
