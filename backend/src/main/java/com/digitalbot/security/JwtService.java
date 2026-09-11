package com.digitalbot.security;

import com.digitalbot.domain.UserAccount;
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

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("id", user.getId())
                .claim("role", user.getRole())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(EXPIRATION_MS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
