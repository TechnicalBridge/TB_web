package com.tbridge.payments.web;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.payments.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/payments/checkout")
    public Map<String, Object> checkout(@AuthenticationPrincipal JwtPrincipal user, @RequestBody Map<String, Object> body) {
        return payments.checkout(user, body);
    }

    @GetMapping("/api/payments")
    public Map<String, Object> list(@AuthenticationPrincipal JwtPrincipal user) {
        return Map.of("payments", payments.list(user));
    }

    @GetMapping("/api/payments/{id}")
    public Map<String, Object> one(@AuthenticationPrincipal JwtPrincipal user, @PathVariable String id) {
        return payments.get(user, id);
    }

    @GetMapping("/api/payments/public/{id}")
    public Map<String, Object> pub(@PathVariable String id, @RequestParam String sig) {
        return payments.publicGet(id, sig);
    }

    @PostMapping("/api/payments/public/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable String id, @RequestParam String sig) {
        return payments.confirmPublic(id, sig);
    }
}
