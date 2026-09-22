package com.tbridge.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;
    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            bytes = Arrays.copyOf(bytes, 32);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(String id, String email, String role, String name) {
        return issue(id, email, role, name, null);
    }

    /** Con RUT: es lo que los servicios usan para acotar lo que cada quien ve. */
    public String issue(String id, String email, String role, String name, String rut) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(id)
                .claim("id", id)
                .claim("email", email)
                .claim("role", role)
                .claim("name", name)
                .claim("rut", rut)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(EXPIRATION_MS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public JwtPrincipal toPrincipal(Claims claims) {
        String id = claims.get("id", String.class);
        if (id == null) {
            id = claims.getSubject();
        }
        return new JwtPrincipal(
                id,
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.get("name", String.class),
                claims.get("rut", String.class)
        );
    }
}
