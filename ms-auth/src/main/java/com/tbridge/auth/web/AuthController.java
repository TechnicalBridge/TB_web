package com.tbridge.auth.web;

import com.tbridge.auth.service.AuthService;
import com.tbridge.common.jwt.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Las puertas del portal.
 *
 * <p>La de adelante es el codigo. El enlace queda como respaldo.
 */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Entrar con RUT y codigo. */
    @PostMapping("/api/auth/acceso")
    public Map<String, Object> acceso(@RequestBody Map<String, String> body, HttpServletRequest peticion) {
        return auth.entrarConCodigo(body.get("rut"), body.get("codigo"), origen(peticion));
    }

    /** Pedir el enlace de respaldo. */
    @PostMapping("/api/auth/enlace")
    public ResponseEntity<Map<String, Object>> enlace(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(auth.pedirEnlace(body.get("rut"), body.get("correo")));
    }

    /** Canjear el enlace por una sesion. */
    @PostMapping("/api/auth/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body, HttpServletRequest peticion) {
        return auth.entrarConEnlace(body.get("token"), origen(peticion));
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal JwtPrincipal user) {
        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("id", user.id());
        usuario.put("nombre", user.name());
        usuario.put("correo", user.email());
        usuario.put("rut", user.rut());
        usuario.put("role", user.role());
        return Map.of("user", usuario);
    }

    /**
     * De donde viene el intento.
     *
     * Se usa solo para contar intentos por origen; de la IP se guarda su
     * huella, nunca la IP.
     */
    private String origen(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }
}
