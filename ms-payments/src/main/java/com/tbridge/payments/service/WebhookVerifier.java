package com.tbridge.payments.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class WebhookVerifier {

    private final String secret;

    public WebhookVerifier(@Value("${app.webhook-secret}") String secret) {
        this.secret = secret;
    }

    public String sign(String paymentId, String amount, String debtId) {
        String payload = paymentId + ":" + normalizeAmount(amount) + ":" + debtId;
        return hmac(payload);
    }

    public boolean matches(String signature, String paymentId, String amount, String debtId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(paymentId, amount, debtId);
        return constantTimeEq(expected, signature);
    }

    public boolean same(String expected, String actual) {
        return expected != null && actual != null && constantTimeEq(expected, actual);
    }

    static String normalizeAmount(String amount) {
        try {
            return new BigDecimal(amount).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return amount == null ? "" : amount;
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el webhook", e);
        }
    }

    private static boolean constantTimeEq(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
