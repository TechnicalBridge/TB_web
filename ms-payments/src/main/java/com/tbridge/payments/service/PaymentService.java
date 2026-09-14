package com.tbridge.payments.service;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.payments.domain.Payment;
import com.tbridge.common.events.PagoExitosoEvent;
import com.tbridge.payments.repo.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<String> GATEWAYS = Set.of("MERCADOPAGO", "KHIPU", "WEBPAY");

    private final PaymentRepository payments;
    private final WebhookVerifier verifier;
    private final EventPublisher events;
    private final String publicUrl;

    public PaymentService(
            PaymentRepository payments,
            WebhookVerifier verifier,
            EventPublisher events,
            @Value("${app.public-url}") String publicUrl
    ) {
        this.payments = payments;
        this.verifier = verifier;
        this.events = events;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    @Transactional
    public Map<String, Object> checkout(JwtPrincipal user, Map<String, Object> body) {
        if (user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El acreedor no paga deudas");
        }
        String debtId = str(body.get("debtId"));
        String installmentId = str(body.get("installmentId"));
        String gateway = str(body.get("gateway")).toUpperCase(Locale.ROOT);
        BigDecimal amount = decimal(body.get("amount"));
        if (debtId.isBlank() || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica la deuda y el monto");
        }
        if (!GATEWAYS.contains(gateway)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pasarela no soportada (Mercado Pago, Khipu o Webpay)");
        }
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setDebtId(debtId);
        payment.setInstallmentId(installmentId.isBlank() ? null : installmentId);
        payment.setDebtorEmail(user.email());
        payment.setAmount(amount);
        payment.setGateway(gateway);
        payment.setStatus("PENDIENTE");
        payment.setCreatedAt(Instant.now());
        payment.setSignature(verifier.sign(payment.getId(), amount.toPlainString(), debtId));
        payments.save(payment);
        Map<String, Object> result = toPublic(payment);
        result.put("checkoutUrl", publicUrl + "/pasarela/" + payment.getId() + "?sig=" + payment.getSignature());
        return result;
    }

    public Map<String, Object> get(JwtPrincipal user, String id) {
        Payment payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (user != null && !user.isCreditor() && !user.email().equalsIgnoreCase(payment.getDebtorEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No puedes ver este pago");
        }
        return toPublic(payment);
    }

    public Map<String, Object> publicGet(String id, String sig) {
        Payment payment = requireSigned(id, sig);
        return toPublic(payment);
    }

    public List<Map<String, Object>> list(JwtPrincipal user) {
        List<Payment> rows = user.isCreditor()
                ? payments.findAll()
                : payments.findByDebtorEmailOrderByCreatedAtDesc(user.email());
        return rows.stream().map(this::toPublic).toList();
    }

    @Transactional
    public Map<String, Object> confirmPublic(String id, String sig) {
        Payment payment = requireSigned(id, sig);
        return markPaid(payment);
    }

    @Transactional
    public Map<String, Object> webhook(String gateway, Map<String, Object> body, String signatureHeader) {
        String id = str(body.get("paymentId"));
        if (id.isBlank()) {
            id = str(body.get("id"));
        }
        Payment payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago desconocido"));
        String sig = signatureHeader == null || signatureHeader.isBlank() ? str(body.get("signature")) : signatureHeader;
        if (!verifier.matches(sig, payment.getId(), payment.getAmount().toPlainString(), payment.getDebtId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma criptográfica inválida");
        }
        if (gateway != null && !gateway.isBlank()) {
            payment.setGateway(gateway.toUpperCase(Locale.ROOT));
        }
        return markPaid(payment);
    }

    private Map<String, Object> markPaid(Payment payment) {
        if ("PAGADO".equals(payment.getStatus())) {
            return toPublic(payment);
        }
        payment.setStatus("PAGADO");
        payment.setPaidAt(Instant.now());
        payments.save(payment);
        events.publishPagoExitoso(new PagoExitosoEvent(
                "pago_exitoso",
                payment.getId(),
                payment.getDebtId(),
                payment.getInstallmentId(),
                payment.getAmount(),
                payment.getGateway(),
                payment.getDebtorEmail(),
                payment.getPaidAt()
        ));
        return toPublic(payment);
    }

    private Payment requireSigned(String id, String sig) {
        Payment payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (!verifier.same(payment.getSignature(), sig)
                && !verifier.matches(sig, payment.getId(), payment.getAmount().toPlainString(), payment.getDebtId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma de checkout inválida");
        }
        return payment;
    }

    private Map<String, Object> toPublic(Payment payment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", payment.getId());
        map.put("debtId", payment.getDebtId());
        map.put("installmentId", payment.getInstallmentId());
        map.put("amount", payment.getAmount());
        map.put("gateway", payment.getGateway());
        map.put("status", payment.getStatus());
        map.put("paidAt", payment.getPaidAt());
        map.put("createdAt", payment.getCreatedAt());
        return map;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
