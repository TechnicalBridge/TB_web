package com.tbridge.auth.web;

import com.tbridge.auth.service.AuthService;
import com.tbridge.common.jwt.JwtPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/magic-link")
    public ResponseEntity<Map<String, Object>> magicLink(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                authService.requestMagicLink(body.get("email"), body.get("name"))
        );
    }

    @PostMapping("/api/auth/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null) {
            token = body.get("uuid");
        }
        return authService.verify(token, body.get("name"));
    }

    @GetMapping("/api/auth/demo")
    public Map<String, Object> demo() {
        return Map.of("accounts", authService.demoAccounts());
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal JwtPrincipal user) {
        return authService.me(user);
    }
}
