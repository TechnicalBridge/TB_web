package com.digitalbot.service;

import com.digitalbot.catalog.PlanCatalog;
import com.digitalbot.domain.PayToken;
import com.digitalbot.domain.Payment;
import com.digitalbot.domain.UserAccount;
import com.digitalbot.dto.UserMapper;
import com.digitalbot.repo.PaymentRepository;
import com.digitalbot.repo.PayTokenRepository;
import com.digitalbot.repo.UserRepository;
import com.digitalbot.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<String> METHODS = Set.of("tarjeta", "transferencia", "billetera");

    private final PaymentRepository payments;
    private final PayTokenRepository tokens;
    private final UserRepository users;
    private final TokenService tokenService;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(
            PaymentRepository payments,
            PayTokenRepository tokens,
            UserRepository users,
            TokenService tokenService
    ) {
        this.payments = payments;
        this.tokens = tokens;
        this.users = users;
        this.tokenService = tokenService;
    }

    public Map<String, Object> list(UserAccount user) {
        List<Map<String, Object>> items = payments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toView)
                .toList();
        int paid = 0;
        int pending = 0;
        for (Map<String, Object> item : items) {
            int amount = (int) item.get("amount");
            String status = (String) item.get("status");
            if ("completado".equals(status)) {
                paid += amount;
            } else if ("pendiente".equals(status)) {
                pending += amount;
            }
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("count", items.size());
        totals.put("paid", paid);
        totals.put("pending", pending);
        return Map.of("payments", items, "totals", totals);
    }

    @Transactional
    public Map<String, Object> charge(UserAccount user, Map<String, Object> body) {
        if (user.isGuest()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Regístrate para completar un pago. El modo invitado solo permite simular."
            );
        }
        String method = String.valueOf(body.getOrDefault("method", ""));
        if (!METHODS.contains(method)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Elige una forma de pago");
        }
        String code = String.valueOf(body.getOrDefault("code", ""));
        PayToken token = tokenService.requireActive(user, code);

        if ("tarjeta".equals(method)) {
            String number = String.valueOf(body.getOrDefault("cardNumber", "")).replaceAll("\\s+", "");
            String holder = String.valueOf(body.getOrDefault("holder", "")).trim();
            String expiry = String.valueOf(body.getOrDefault("expiry", "")).trim();
            String cvv = String.valueOf(body.getOrDefault("cvv", "")).trim();
            if (!number.matches("^\\d{16}$")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La tarjeta debe tener 16 dígitos (simulación)");
            }
            if (holder.length() < 3) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Indica el titular");
            }
            if (!expiry.matches("^\\d{2}/\\d{2}$")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Caducidad en formato MM/AA");
            }
            if (!cvv.matches("^\\d{3}$")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CVV de 3 dígitos");
            }
        }

        Instant now = Instant.now();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setUserId(user.getId());
        payment.setPlanId(token.getPlanId());
        payment.setAmount(token.getAmount());
        payment.setCurrency(token.getCurrency());
        payment.setMethod(method);
        payment.setStatus("completado");
        payment.setCreatedAt(now);
        if ("tarjeta".equals(method)) {
            String number = String.valueOf(body.get("cardNumber")).replaceAll("\\s+", "");
            payment.setLast4(number.substring(number.length() - 4));
        }
        byte[] ref = new byte[3];
        random.nextBytes(ref);
        payment.setReference("DBP-" + HexFormat.of().withUpperCase().formatHex(ref));
        payment.setBilling(token.getBilling());
        payment.setTokenCode(token.getCode());

        token.setStatus("usado");
        token.setUsedAt(now);
        user.setPlanId(token.getPlanId());
        tokens.save(token);
        users.save(user);
        payments.save(payment);

        return Map.of("payment", toView(payment), "user", UserMapper.publicUser(user));
    }

    private Map<String, Object> toView(Payment payment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", payment.getId());
        map.put("userId", payment.getUserId());
        map.put("planId", payment.getPlanId());
        map.put("amount", payment.getAmount());
        map.put("currency", payment.getCurrency());
        map.put("method", payment.getMethod());
        map.put("status", payment.getStatus());
        map.put("createdAt", payment.getCreatedAt());
        map.put("last4", payment.getLast4());
        map.put("reference", payment.getReference());
        map.put("billing", payment.getBilling());
        map.put("tokenCode", payment.getTokenCode());
        map.put("plan", PlanCatalog.find(payment.getPlanId()));
        return map;
    }
}
