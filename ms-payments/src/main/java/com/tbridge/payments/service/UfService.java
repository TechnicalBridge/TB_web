package com.tbridge.payments.service;

import com.tbridge.common.web.ApiException;
import com.tbridge.payments.domain.UfValue;
import com.tbridge.payments.repo.UfValueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Convierte UF a pesos con el valor del dia.
 *
 * <p>La UF cambia todos los dias, asi que una deuda en UF no tiene un precio
 * en pesos hasta el momento en que se paga. Ese valor se guarda junto al pago
 * porque sin el nadie puede reconstruir despues por que UF 38,5 fueron esos
 * pesos y no otros.
 *
 * <p>Si no hay valor para el dia, el cobro se detiene. Inventar uno —tomar el
 * del mes pasado, redondear -- seria cobrar una cifra que despues nadie podria
 * explicar.
 */
@Service
public class UfService {

    private final UfValueRepository valores;

    public UfService(UfValueRepository valores) {
        this.valores = valores;
    }

    public UfValue delDia(LocalDate dia) {
        return valores.findFirstByDayLessThanEqualOrderByDayDesc(dia)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No hay valor de la UF para el " + dia + ": el cobro en UF no puede continuar"
                ));
    }

    /** Pesos, redondeados al entero: no existe el medio peso. */
    public long aPesos(BigDecimal montoUf, BigDecimal valorUf) {
        return montoUf.multiply(valorUf).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
