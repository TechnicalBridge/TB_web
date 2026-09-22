package com.tbridge.payments.web;

import com.tbridge.common.web.ApiException;
import com.tbridge.payments.domain.UfValue;
import com.tbridge.payments.repo.UfValueRepository;
import com.tbridge.payments.service.UfLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Lo que operaciones puede hacer con la UF. No es publico: va detras de la
 * clave interna y el gateway no lo expone.
 */
@RestController
public class InternalController {

    private final UfValueRepository valores;
    private final UfLoader cargador;
    private final String internalKey;

    public InternalController(UfValueRepository valores, UfLoader cargador,
                              @Value("${app.internal-key}") String internalKey) {
        this.valores = valores;
        this.cargador = cargador;
        this.internalKey = internalKey;
    }

    private void exigirClave(String clave) {
        if (clave == null || !clave.equals(internalKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave interna invalida");
        }
    }

    /** Carga a mano el valor de un dia, cuando el Banco Central no esta disponible. */
    @PostMapping("/internal/uf")
    public Map<String, Object> cargarAMano(
            @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @RequestBody Map<String, String> body
    ) {
        exigirClave(clave);
        LocalDate dia;
        BigDecimal valor;
        try {
            dia = LocalDate.parse(body.get("dia"));
            valor = new BigDecimal(body.get("valor"));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Se espera { \"dia\": \"2026-09-22\", \"valor\": \"39876.54\" }");
        }
        if (valor.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La UF tiene que ser positiva");
        }
        valores.save(new UfValue(dia, valor.setScale(2, RoundingMode.HALF_UP), "manual"));
        return Map.of("dia", dia, "valor", valor, "fuente", "manual");
    }

    /** Pide ahora la carga desde el Banco Central, sin esperar la de la manana. */
    @PostMapping("/internal/uf/cargar")
    public Map<String, Object> cargarAhora(@RequestHeader(value = "X-Internal-Key", required = false) String clave) {
        exigirClave(clave);
        return cargador.cargar();
    }
}
