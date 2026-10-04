package com.ecommerce.ecommerce_system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String secret;
    public JwtUtil(@org.springframework.beans.factory.annotation.Value("${JWT_SECRET:}") String configured) {
        if (configured.isBlank()) {
            byte[] bytes = new byte[48]; new java.security.SecureRandom().nextBytes(bytes);
            secret = java.util.Base64.getEncoder().encodeToString(bytes);
            System.err.println("JWT_SECRET is unset: using an ephemeral key; sessions expire on restart. Configure a stable secret for deployment.");
        } else {
            if (configured.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
            secret = configured;
        }
    }
    private String credentialFingerprint(String passwordHash) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private final long expirationMs = 86400000; // 24 hours

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String role, String passwordHash) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("credential", credentialFingerprint(passwordHash))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String username, String passwordHash) {
        return username.equals(extractUsername(token)) && !isExpired(token) && credentialFingerprint(passwordHash).equals(extractClaim(token, c -> c.get("credential", String.class)));
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}