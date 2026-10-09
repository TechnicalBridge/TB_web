package com.tbridge.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Las deudas del deudor, pedidas a ms-debt con SU sesion.
 *
 * <p>ms-debt decide que puede ver. El asistente no tiene acceso propio a la
 * base, y por eso es de solo lectura por construccion, no por promesa.
 */
@Component
public class DeudasClient {

    private static final Logger log = LoggerFactory.getLogger(DeudasClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> OBJETO = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;

    @Autowired
    public DeudasClient(@Value("${app.debt-url}") String debtUrl) {
        this(RestClient.builder().requestFactory(tiempos()), debtUrl);
    }

    /** Con otro constructor de RestClient: para probarlo sin un ms-debt de verdad. */
    DeudasClient(RestClient.Builder builder, String debtUrl) {
        this.rest = builder.baseUrl(debtUrl.replaceAll("/+$", "")).build();
    }

    private static SimpleClientHttpRequestFactory tiempos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(8_000);
        return fabrica;
    }

    /** Si ms-debt no responde o dice que no, el asistente sigue: sin deudas que mostrar. */
    public List<Map<String, Object>> deudasDelDeudor(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            return List.of();
        }
        try {
            return deudasDe(rest.get().uri("/api/debts")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(OBJETO));
        } catch (RuntimeException fallo) {
            log.warn("ms-debt no entrego las deudas del deudor: {}", fallo.getMessage());
            return List.of();
        }
    }

    /**
     * La lista que viene en la respuesta de GET /api/debts.
     *
     * <p>ms-debt responde en HAL: las deudas estan en {@code _embedded.debts}, y
     * una lista vacia simplemente no trae {@code _embedded}.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> deudasDe(Map<String, Object> respuesta) {
        if (respuesta != null && respuesta.get("_embedded") instanceof Map<?, ?> embebido
                && embebido.get("debts") instanceof List<?> deudas) {
            return deudas.stream()
                    .filter(Map.class::isInstance)
                    .map(deuda -> (Map<String, Object>) deuda)
                    .toList();
        }
        return List.of();
    }
}
