package com.tbridge.payments.client;

import com.tbridge.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * Lo que ms-payments necesita saber de una deuda, preguntandoselo a ms-debt.
 *
 * <p><b>Por que existe.</b> Antes el monto a cobrar venia en el cuerpo de la
 * peticion, es decir, lo decidia el navegador. Quien supiera el id de una
 * deuda podia pagar un peso y darla por saldada. Ahora el monto y a quien se
 * le debe salen del servicio que manda sobre eso.
 *
 * <pre>
 *   GET /internal/debts/{id}?installmentId={opcional}
 *   X-Internal-Key: ...
 *   -> { debtId, creditorRut, debtorRut, currency, amount, installmentId }
 * </pre>
 */
@Component
public class DebtClient {

    /** Cuanto se debe y a quien, segun ms-debt. */
    public record DebtSnapshot(
            Long debtId,
            String creditorRut,
            String debtorRut,
            String currency,
            BigDecimal amount,
            Long installmentId
    ) {}

    private final RestClient rest;
    private final String internalKey;

    public DebtClient(@Value("${app.debt-url}") String debtUrl,
                      @Value("${app.internal-key}") String internalKey) {
        this.rest = RestClient.builder().baseUrl(debtUrl.replaceAll("/$", "")).build();
        this.internalKey = internalKey;
    }

    /**
     * La deuda —o la cuota— que se va a pagar.
     *
     * <p>Si ms-debt no responde, el pago no se inicia. Es a proposito: cobrar
     * sin saber cuanto ni a quien es peor que no cobrar. Si ms-debt responde
     * con un error propio (la deuda no existe, no tiene saldo), se devuelve el
     * mismo codigo.
     */
    public DebtSnapshot obtener(Long debtId, Long installmentId) {
        try {
            DebtSnapshot deuda = rest.get()
                    .uri("/internal/debts/{id}?installmentId={cuota}", debtId,
                         installmentId == null ? "" : installmentId)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .body(DebtSnapshot.class);
            if (deuda == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada");
            }
            return deuda;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada");
        } catch (HttpClientErrorException.Conflict e) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa deuda no tiene saldo por pagar");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar la deuda: el cobro no se inicia a ciegas");
        }
    }
}
