package com.tbridge.payments.service;

import com.tbridge.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * Lo que ms-payments necesita saber de una deuda, preguntandoselo a ms-debt.
 *
 * <p><b>Por que existe.</b> Antes el monto a cobrar venia en el cuerpo de la
 * peticion, es decir, lo decidia el navegador. Quien supiera el id de una
 * deuda podia pagar un peso y darla por saldada. Ahora el monto y a quien se
 * le debe salen del servicio que manda sobre eso, y el cliente solo dice cual
 * deuda quiere pagar.
 *
 * <p>ms-debt tiene que exponer:
 * <pre>
 *   GET /internal/debts/{id}?installmentId={opcional}
 *   X-Internal-Key: ...
 *   -> { debtId, creditorRut, debtorRut, currency, amount, installmentId }
 * </pre>
 */
@Service
public class DebtGateway {

    public record DebtSnapshot(
            Long debtId,
            String creditorRut,
            String debtorRut,
            String currency,
            BigDecimal amount,
            Long installmentId
    ) {}

    private final RestClient rest;
    private final String debtUrl;
    private final String internalKey;

    public DebtGateway(
            @Value("${app.debt-url}") String debtUrl,
            @Value("${app.internal-key}") String internalKey
    ) {
        this.rest = RestClient.create();
        this.debtUrl = debtUrl.replaceAll("/$", "");
        this.internalKey = internalKey;
    }

    /**
     * La deuda —o la cuota— que se va a pagar.
     *
     * Si ms-debt no responde, el pago no se inicia. Es a proposito: cobrar sin
     * saber cuanto ni a quien es peor que no cobrar.
     */
    public DebtSnapshot obtener(Long debtId, Long installmentId, String debtorRut) {
        try {
            DebtSnapshot deuda = rest.get()
                    .uri(debtUrl + "/internal/debts/{id}?installmentId={cuota}", debtId,
                         installmentId == null ? "" : installmentId)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .body(DebtSnapshot.class);

            if (deuda == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada");
            }
            // El deudor solo puede pagar lo suyo. La comprobacion la hace quien
            // conoce la deuda, no quien pide pagarla.
            if (debtorRut != null && !debtorRut.equalsIgnoreCase(deuda.debtorRut())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Esa deuda no es tuya");
            }
            return deuda;
        } catch (RestClientException e) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar la deuda: el cobro no se inicia a ciegas"
            );
        }
    }
}
