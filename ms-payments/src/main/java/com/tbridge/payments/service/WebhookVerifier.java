package com.tbridge.payments.service;

import com.tbridge.common.util.Hash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * La firma de los enlaces de pago y de los avisos de la pasarela.
 *
 * <p>HMAC-SHA256 de "pago:monto:deuda". El monto se normaliza antes de firmar
 * (150000, 150000.0 y 150000.00 son lo mismo), para que la firma no dependa de
 * como lo escribio cada lado.
 */
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
        return Hash.igualesEnTiempoConstante(expected, signature);
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
}
