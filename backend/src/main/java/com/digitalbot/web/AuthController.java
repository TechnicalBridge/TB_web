package com.digitalbot.web;

import com.digitalbot.domain.UserAccount;
import com.digitalbot.dto.UserMapper;
import com.digitalbot.service.AuthService;
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

    @PostMapping("/api/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(
                body.get("name"),
                body.get("email"),
                body.get("password")
        ));
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        return authService.login(body.get("email"), body.get("password"));
    }

    @PostMapping("/api/auth/guest")
    public ResponseEntity<Map<String, Object>> guest() {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.guest());
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserAccount user) {
        return Map.of("user", UserMapper.publicUser(user));
    }
}
