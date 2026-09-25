package com.tbridge.debt.client;

import com.tbridge.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que ms-debt le pide a ms-auth: emitir el codigo de acceso de un deudor.
 *
 * <p>ms-auth genera el codigo y lo manda al correo. La respuesta trae el
 * codigo, pero aca se lee solo cuando vence: el codigo nunca llega a quien lo
 * pidio. Si el personal de la empresa pudiera verlo, podria entrar como el
 * deudor.
 */
@Component
public class AuthClient {

    /**
     * Lo que se le pide a ms-auth. Con {@code vence}, el correo es el
     * recordatorio de una cuota que vence ese dia; sin el, es el primer aviso.
     */
    public record PedidoDeCodigo(String rut, List<String> canales, String correo, String acreedor, String paraQue,
                                 LocalDate vence) {

        public PedidoDeCodigo(String rut, List<String> canales, String correo, String acreedor, String paraQue) {
            this(rut, canales, correo, acreedor, paraQue, null);
        }
    }

    /** Lo unico que se lee de la respuesta. */
    public record CodigoEmitido(Instant expiraEn) {}

    private final RestClient rest;
    private final String internalKey;

    public AuthClient(@Value("${app.auth-url}") String authUrl,
                      @Value("${app.internal-key}") String internalKey) {
        this.rest = RestClient.builder().baseUrl(authUrl.replaceAll("/$", "")).build();
        this.internalKey = internalKey;
    }

    public CodigoEmitido emitirCodigo(PedidoDeCodigo pedido) {
        try {
            return rest.post()
                    .uri("/internal/codigos")
                    .header("X-Internal-Key", internalKey)
                    .body(pedido)
                    .retrieve()
                    .body(CodigoEmitido.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo emitir el codigo: el servicio de acceso no responde");
        }
    }
}
