package com.tbridge.payments.client;

import com.tbridge.common.exception.ApiError;
import com.tbridge.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que ms-payments necesita saber de una deuda, preguntandoselo a ms-debt.
 *
 * <p><b>Por que existe.</b> Antes el monto a cobrar venia en el cuerpo de la
 * peticion, es decir, lo decidia el navegador. Quien supiera el id de una
 * deuda podia pagar un peso y darla por saldada. Ahora el monto y a quien se
 * le debe salen del servicio que manda sobre eso.
 *
 * <pre>
 *   GET /internal/debts/{id}?installmentIds=12&installmentIds=13   (opcional)
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
     * La deuda —o las cuotas— que se van a pagar.
     *
     * <p>Si ms-debt no responde, el pago no se inicia. Es a proposito: cobrar
     * sin saber cuanto ni a quien es peor que no cobrar. Si ms-debt responde
     * con un error propio (la deuda no existe, no tiene saldo, las cuotas no
     * van en orden), se devuelve el mismo codigo con el mismo texto: esta
     * escrito para el deudor.
     */
    public DebtSnapshot obtener(Long debtId, List<Long> installmentIds) {
        try {
            DebtSnapshot deuda = rest.get()
                    .uri(uri -> {
                        uri.path("/internal/debts/{id}");
                        if (installmentIds != null && !installmentIds.isEmpty()) {
                            uri.queryParam("installmentIds", installmentIds.toArray());
                        }
                        return uri.build(debtId);
                    })
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .body(DebtSnapshot.class);
            if (deuda == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Deuda no encontrada");
            }
            return deuda;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, textoDe(e, "Deuda no encontrada"));
        } catch (HttpClientErrorException.Conflict e) {
            throw new ApiException(HttpStatus.CONFLICT, textoDe(e, "Esa deuda no tiene saldo por pagar"));
        } catch (HttpClientErrorException.BadRequest e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, textoDe(e, "Esas cuotas no se pueden pagar"));
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar la deuda: el cobro no se inicia a ciegas");
        }
    }

    /** El texto del error de ms-debt, o uno por omision si no se puede leer. */
    private static String textoDe(HttpClientErrorException e, String porOmision) {
        try {
            ApiError error = e.getResponseBodyAs(ApiError.class);
            return error == null || error.error() == null || error.error().isBlank() ? porOmision : error.error();
        } catch (RuntimeException ilegible) {
            return porOmision;
        }
    }
}
