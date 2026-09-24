package com.tbridge.payments.service;

import com.tbridge.common.exception.ApiException;
import com.tbridge.payments.dto.response.UfResponse;
import com.tbridge.payments.model.UfValue;
import com.tbridge.payments.repository.UfValueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * La UF de ESE dia, y ninguna otra.
     *
     * <p>Antes se usaba la mas reciente disponible. Con la UF eso cobra mal en
     * silencio: la de ayer no es la de hoy. El Banco Central la publica con
     * un mes de adelanto y {@link UfLoader} la carga asi, de modo que si falta
     * es porque algo fallo, y es mejor detener el cobro que adivinar.
     */
    public UfValue delDia(LocalDate dia) {
        return valores.findById(dia)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No hay valor de la UF para el " + dia + ": el cobro en UF no puede continuar"
                ));
    }

    /** Pesos, redondeados al entero: no existe el medio peso. */
    public long aPesos(BigDecimal montoUf, BigDecimal valorUf) {
        return montoUf.multiply(valorUf).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Carga a mano el valor de un dia, cuando el Banco Central no esta disponible. */
    @Transactional
    public UfResponse cargarAMano(LocalDate dia, BigDecimal valor) {
        if (valor.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La UF tiene que ser positiva");
        }
        UfValue guardado = valores.save(new UfValue(dia, valor.setScale(2, RoundingMode.HALF_UP), "manual"));
        return new UfResponse(guardado.getDay(), guardado.getValue(), guardado.getSource());
    }
}
