package com.digitalbot.web;

import com.digitalbot.domain.UserAccount;
import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/api/tokens")
    public ResponseEntity<Map<String, Object>> issue(
            @AuthenticationPrincipal UserAccount user,
            @RequestBody(required = false) QuoteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenService.issue(user, request));
    }

    @PostMapping("/api/tokens/verify")
    public Map<String, Object> verify(
            @AuthenticationPrincipal UserAccount user,
            @RequestBody Map<String, String> body
    ) {
        return tokenService.verify(user, body.get("code"));
    }
}
