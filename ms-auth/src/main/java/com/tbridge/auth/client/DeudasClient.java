package com.tbridge.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Lo que ms-auth le pregunta a ms-debt: el correo que registro el acreedor
 * para un RUT. El enlace de acceso de un deudor va solo ahi.
 *
 * <p>Si ms-debt no responde, no hay correo: no sale ningun enlace. Mejor que
 * el deudor espere a que vuelva, que mandarle su acceso a cualquiera.
 */
@Component
public class DeudasClient {

    private static final Logger log = LoggerFactory.getLogger(DeudasClient.class);

    private final RestClient rest;
    private final String internalKey;

    @Autowired
    public DeudasClient(@Value("${app.debt-url}") String debtUrl, @Value("${app.internal-key}") String internalKey) {
        this(RestClient.builder().requestFactory(tiempos()), debtUrl, internalKey);
    }

    /** Con otro constructor de RestClient: para probarlo sin un ms-debt de verdad. */
    DeudasClient(RestClient.Builder builder, String debtUrl, String internalKey) {
        this.rest = builder.baseUrl(debtUrl.replaceAll("/$", "")).build();
        this.internalKey = internalKey;
    }

    private static SimpleClientHttpRequestFactory tiempos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(3_000);
        return fabrica;
    }

    /** El correo registrado para ese RUT. Vacio si no hay, o si ms-debt no responde. */
    public Optional<String> correoDelDeudor(String rut) {
        try {
            Correo respuesta = rest.get().uri("/internal/deudores/{rut}/correo", rut)
                    .header("X-Internal-Key", internalKey)
                    .retrieve()
                    .body(Correo.class);
            return Optional.ofNullable(respuesta).map(Correo::correo).filter(c -> c.contains("@"));
        } catch (HttpClientErrorException.NotFound sinCorreo) {
            return Optional.empty();
        } catch (RestClientException fallo) {
            log.warn("ms-debt no respondio por el correo de un deudor: {}", fallo.getMessage());
            return Optional.empty();
        }
    }

    record Correo(String correo) {}
}
