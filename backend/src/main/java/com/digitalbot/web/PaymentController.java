package com.digitalbot.web;

import com.digitalbot.domain.UserAccount;
import com.digitalbot.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/api/payments")
    public Map<String, Object> list(@AuthenticationPrincipal UserAccount user) {
        return paymentService.list(user);
    }

    @PostMapping("/api/payments")
    public ResponseEntity<Map<String, Object>> charge(
            @AuthenticationPrincipal UserAccount user,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.charge(user, body));
    }
}
