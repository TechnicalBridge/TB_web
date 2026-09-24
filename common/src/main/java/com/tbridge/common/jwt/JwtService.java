package com.tbridge.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Firma y lee el JWT de la sesion.
 *
 * <p><b>El JWT dura poco a proposito</b> —15 minutos por omision—. Es un token
 * que ningun servicio puede revocar: se valida solo con la firma, sin
 * preguntarle a nadie. Lo que mantiene a una persona adentro mas tiempo es la
 * llave de renovacion, que si se puede revocar (ver SessionService en
 * ms-auth). Antes duraba siete dias, y cerrar sesion solo lo borraba del
 * navegador: quien lo hubiera copiado seguia adentro una semana.
 */
@Service
public class JwtService {

    /** HMAC-SHA256 necesita una llave de 256 bits. */
    private static final int BYTES_MINIMOS = 32;

    private final SecretKey key;
    private final Duration vida;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.ttl-minutes:15}") long minutos) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        //  Antes un secreto corto se rellenaba con ceros hasta los 32 bytes y
        //  el servicio arrancaba como si nada: un JWT_SECRET de cinco letras
        //  quedaba aceptado con casi nada de entropia. Mejor no arrancar.
        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException("jwt.secret tiene " + bytes.length
                    + " bytes y necesita al menos " + BYTES_MINIMOS + ". Revisa JWT_SECRET.");
        }
        if (minutos < 1) {
            throw new IllegalStateException("jwt.ttl-minutes tiene que ser al menos 1");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.vida = Duration.ofMinutes(minutos);
    }

    /** Cuanto dura un JWT recien emitido. */
    public Duration vida() {
        return vida;
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
                .expiration(Date.from(now.plus(vida)))
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
