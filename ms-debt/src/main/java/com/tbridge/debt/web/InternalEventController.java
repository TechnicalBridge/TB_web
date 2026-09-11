package com.tbridge.debt.web;

import com.tbridge.common.web.ApiException;
import com.tbridge.common.events.PagoExitosoEvent;
import com.tbridge.debt.service.DebtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InternalEventController {

    private final DebtService debts;
    private final String internalKey;

    public InternalEventController(DebtService debts, @Value("${app.internal-key}") String internalKey) {
        this.debts = debts;
        this.internalKey = internalKey;
    }

    @PostMapping("/internal/events/pago-exitoso")
    public Map<String, Object> pagoExitoso(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestBody PagoExitosoEvent event
    ) {
        if (key == null || !key.equals(internalKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave interna inválida");
        }
        debts.onPagoExitoso(event);
        return Map.of("ok", true);
    }
}
