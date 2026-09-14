package com.tbridge.payments.web;

import com.tbridge.payments.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WebhookController {

    private final PaymentService payments;

    public WebhookController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/payments/webhooks/{gateway}")
    public Map<String, Object> webhook(
            @PathVariable String gateway,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody Map<String, Object> body
    ) {
        return payments.webhook(gateway, body, signature);
    }
}
