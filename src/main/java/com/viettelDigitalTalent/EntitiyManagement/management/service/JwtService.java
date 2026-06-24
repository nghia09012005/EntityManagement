package com.viettelDigitalTalent.EntitiyManagement.management.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(String username, String tenantId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("tenantId", tenantId, "role", role))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            Claims c = parse(token);
            return c.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) { return parse(token).getSubject(); }
    public String extractTenantId(String token)  { return parse(token).get("tenantId", String.class); }
    public String extractRole(String token)       { return parse(token).get("role", String.class); }
}
