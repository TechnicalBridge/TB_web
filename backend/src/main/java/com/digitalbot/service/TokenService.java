package com.digitalbot.service;

import com.digitalbot.catalog.PlanCatalog;
import com.digitalbot.domain.PayToken;
import com.digitalbot.domain.UserAccount;
import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.dto.QuoteResult;
import com.digitalbot.repo.PayTokenRepository;
import com.digitalbot.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {

    private final PayTokenRepository tokens;
    private final QuoteService quoteService;
    private final SecureRandom random = new SecureRandom();

    public TokenService(PayTokenRepository tokens, QuoteService quoteService) {
        this.tokens = tokens;
        this.quoteService = quoteService;
    }

    @Transactional
    public Map<String, Object> issue(UserAccount user, QuoteRequest request) {
        if (user.isGuest()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "El invitado puede simular, pero debe registrarse para emitir un token de pago"
            );
        }
        QuoteResult quoted = quoteService.quote(request);
        tokens.findByUserIdAndStatus(user.getId(), "activo").forEach(token -> {
            token.setStatus("revocado");
            tokens.save(token);
        });

        Instant now = Instant.now();
        PayToken token = new PayToken();
        token.setId(UUID.randomUUID().toString());
        token.setCode(issueCode());
        token.setUserId(user.getId());
        token.setPlanId(quoted.plan().id());
        token.setAmount(quoted.amount());
        token.setCurrency(quoted.currency());
        token.setBilling(quoted.billing());
        token.setUsers(quoted.users());
        token.setExtras(quoted.extras());
        token.setStatus("activo");
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
        tokens.save(token);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", publicToken(token));
        body.put("verification", List.of(
                Map.of("step", "Identidad", "detail", user.getEmail(), "ok", true),
                Map.of("step", "Plan", "detail", quoted.plan().name(), "ok", true),
                Map.of("step", "Importe", "detail", quoted.amount() + " " + quoted.currency(), "ok", true),
                Map.of("step", "Firma", "detail", "Sistema automatizado verificado", "ok", true)
        ));
        return body;
    }

    @Transactional
    public Map<String, Object> verify(UserAccount user, String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Introduce el token");
        }
        PayToken token = tokens.findByCodeAndUserId(code, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Token no encontrado"));
        if (!"activo".equals(token.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Este token ya fue usado o revocado");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setStatus("caducado");
            tokens.save(token);
            throw new ApiException(HttpStatus.GONE, "El token caducó. Emite uno nuevo.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", true);
        body.put("token", publicToken(token));
        body.put("methods", List.of(
                Map.of("id", "tarjeta", "name", "Tarjeta", "detail", "Crédito o débito · simulación"),
                Map.of("id", "transferencia", "name", "Transferencia", "detail", "Banco · referencia DIGITAL BOT"),
                Map.of("id", "billetera", "name", "Billetera digital", "detail", "Saldo simulado DIGITAL BOT")
        ));
        return body;
    }

    public PayToken requireActive(UserAccount user, String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim().toUpperCase();
        PayToken token = tokens.findByCodeAndUserId(code, user.getId()).orElse(null);
        if (token == null || !"activo".equals(token.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token inválido. Verifícalo de nuevo.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setStatus("caducado");
            tokens.save(token);
            throw new ApiException(HttpStatus.GONE, "El token caducó");
        }
        return token;
    }

    private Map<String, Object> publicToken(PayToken token) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", token.getCode());
        map.put("amount", token.getAmount());
        map.put("currency", token.getCurrency());
        map.put("billing", token.getBilling());
        map.put("plan", PlanCatalog.find(token.getPlanId()));
        map.put("extras", token.getExtras());
        map.put("users", token.getUsers());
        map.put("expiresAt", token.getExpiresAt());
        map.put("status", token.getStatus());
        return map;
    }

    private String issueCode() {
        return "DBT-" + part() + "-" + part() + "-" + part();
    }

    private String part() {
        byte[] bytes = new byte[2];
        random.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
