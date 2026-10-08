package com.tbridge.payments.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.tbridge.payments.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente REST para la API de Mercado Pago (Checkout Pro y Pagos v1).
 *
 * <pre>
 *   POST /checkout/preferences          crea la preferencia de cobro  -> {id, init_point, sandbox_init_point}
 *   GET  /merchant_orders/search        las ordenes de una preferencia -> {elements: [{payments: [{id, status}]}]}
 *   GET  /v1/payments/{id}              obtiene el estado del pago   -> {status, status_detail, amount, ...}
 *   Authorization: Bearer <TOKEN> credencial de la aplicacion
 * </pre>
 *
 * <p>Dos cosas que hace distinto Mercado Pago y hay que respetar:</p>
 * <ul>
 *   <li><b>Las back_urls de http:// se descartan.</b> Mercado Pago las borra
 *       sin avisar si no son https, y si se pidio {@code auto_return} la
 *       preferencia entera se rechaza con {@code 400 invalid_auto_return}
 *       ("auto_return invalid. back_url.success must be defined"). Por eso
 *       {@code auto_return} solo se manda cuando la vuelta es https.</li>
 *   <li><b>Los pagos de una preferencia estan en sus ordenes.</b> La
 *       preferencia misma no los trae (no tiene {@code payments}). Al pagarse,
 *       Mercado Pago abre una orden con el id de la preferencia y los intentos
 *       de pago: con eso se concilia sin depender de que el deudor vuelva al
 *       portal ni de que el aviso llegue.</li>
 * </ul>
 */
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final String PREFERENCIAS = "/checkout/preferences";
    private static final String PAGOS = "/v1/payments";
    private static final String ORDENES = "/merchant_orders/search";

    /** La preferencia de pago generada en Mercado Pago. */
    public record Preferencia(String id, String initPoint, String sandboxInitPoint) {
        public String url(boolean testMode) {
            // Mercado Pago unificó el checkout en init_point; el subdominio sandbox está discontinuado
            // y muestra "Algo anda mal". Las credenciales de prueba activan el modo test automáticamente.
            return initPoint != null && !initPoint.isBlank() ? initPoint : sandboxInitPoint;
        }
    }

    /** Estado de un pago consultado directamente a la API de Mercado Pago. */
    public record PagoInfo(Long id, String status, String statusDetail, BigDecimal transactionAmount,
                           String currencyId, String externalReference, JsonNode crudo) {
        public boolean pagado() {
            return "approved".equalsIgnoreCase(status);
        }
    }

    /**
     * Un pago hecho sobre una preferencia, tal como lo reporta Mercado Pago.
     * {@code id} y {@code status} vienen null si todavia no se pago nada.
     */
    public record PagoDePreferencia(Long id, String status, JsonNode crudo) {
        public boolean pagado() {
            return id != null && "approved".equalsIgnoreCase(status);
        }
    }

    /** Lo que se sabe de la preferencia mas adelante, para conciliar el pago. */
    public record EstadoPreferencia(String id, String externalReference, Long totalAmount,
                                    PagoDePreferencia pago) {}

    /**
     * Si Mercado Pago se va a quedar con esta direccion de vuelta.
     *
     * <p>Las back_urls que no son https las descarta en silencio (las deja
     * vacias), asi que en local, con {@code http://localhost:5173}, la vuelta
     * no existe para Mercado Pago aunque DataBridge la sirva bien.</p>
     */
    static boolean vueltaQueMercadoPagoAcepta(String url) {
        return url != null && url.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    private final RestClient rest;
    private final String accessToken;
    private final String publicKey;
    private final boolean real;
    private final boolean testMode;

    @Autowired
    public MercadoPagoClient(
            @Value("${app.mercadopago.url:https://api.mercadopago.com}") String apiUrl,
            @Value("${app.mercadopago.access-token:}") String accessToken,
            @Value("${app.mercadopago.public-key:}") String publicKey,
            @Value("${app.mercadopago.environment:TEST}") String environment
    ) {
        this(apiUrl, accessToken, publicKey, environment, TiemposDePasarela.fabrica());
    }

    /** Con otros tiempos maximos: para probarlos sin esperar veinte segundos. */
    MercadoPagoClient(String apiUrl, String accessToken, String publicKey, String environment,
                      ClientHttpRequestFactory fabrica) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.real = !this.accessToken.isBlank() && !"SIMULADA".equalsIgnoreCase(environment == null ? "" : environment.trim());
        this.testMode = "TEST".equalsIgnoreCase(environment == null ? "" : environment.trim())
                || "SANDBOX".equalsIgnoreCase(environment == null ? "" : environment.trim());
        this.rest = RestClient.builder()
                .requestFactory(fabrica)
                .baseUrl(apiUrl.replaceAll("/$", ""))
                .defaultHeader("Authorization", "Bearer " + this.accessToken)
                .build();
    }

    /** Si Mercado Pago cobra de verdad (hay access token configurado) o se usa simulacion. */
    public boolean real() {
        return real;
    }

    public boolean testMode() {
        return testMode;
    }

    public String getPublicKey() {
        return publicKey;
    }

    /**
     * Crea una preferencia de pago en Mercado Pago Checkout Pro.
     *
     * @param returnUrl donde vuelve el deudor. Si no es https, Mercado Pago la
     *                  descarta y el deudor no vuelve solo: el pago se concilia
     *                  contra la preferencia ({@link #consultarPreferencia}).
     */
    public Preferencia crearPreferencia(String externalReference, String titulo, long montoClp,
                                       String emailPayer, String returnUrl) {
        return crearPreferencia(externalReference, titulo, montoClp, emailPayer, returnUrl, null);
    }

    /** Mercado Pago pide las fechas con milisegundos y zona: 2026-10-06T02:30:00.000-03:00. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * Igual, pero la preferencia vence en {@code vence}: pasado eso, Mercado Pago
     * ya no la deja pagar. Asi un cobro que DataBridge da por vencido no se
     * puede pagar despues sin que nadie lo abone.
     */
    public Preferencia crearPreferencia(String externalReference, String titulo, long montoClp,
                                       String emailPayer, String returnUrl, OffsetDateTime vence) {
        try {
            log.info("Creando preferencia Mercado Pago: ref={}, monto={}", externalReference, montoClp);

            Item item = new Item("item-" + externalReference, titulo, "Pago de cuotas / deuda", 1, montoClp, "CLP");
            Payer payer = new Payer(emailPayer != null && !emailPayer.isBlank() ? emailPayer : "test_user_660778198@testuser.com");
            BackUrls backUrls = new BackUrls(returnUrl, returnUrl, returnUrl);

            //  auto_return solo si la vuelta es https: Mercado Pago valida la
            //  back_url.success DESPUES de borrar las que no son https, y si la
            //  encontro vacia rechaza la preferencia entera con 400.
            boolean vuelveSiMismo = vueltaQueMercadoPagoAcepta(returnUrl);
            if (!vuelveSiMismo) {
                log.warn("La vuelta de Mercado Pago (PUBLIC_URL) no es https: Mercado Pago la va a descartar, "
                        + "el deudor no volvera solo y no se pedira auto_return. El pago se concilia por la "
                        + "preferencia. PUBLIC_URL=https://... lo arregla.");
            }

            CrearPreferencia payload = new CrearPreferencia(
                    List.of(item),
                    payer,
                    backUrls,
                    vuelveSiMismo ? "approved" : null,
                    externalReference,
                    "TECHNICAL BRIDGE",
                    vence == null ? null : true,
                    vence == null ? null : vence.truncatedTo(ChronoUnit.SECONDS).format(FECHA)
            );

            JsonNode r = rest.post()
                    .uri(PREFERENCIAS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (r == null || !r.hasNonNull("id")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Mercado Pago no devolvió la preferencia");
            }

            String id = r.get("id").asText();
            String initPoint = r.hasNonNull("init_point") ? r.get("init_point").asText() : null;
            String sandboxInitPoint = r.hasNonNull("sandbox_init_point") ? r.get("sandbox_init_point").asText() : null;

            if (vuelveSiMismo && r.path("back_urls").path("success").asText("").isBlank()) {
                log.warn("Mercado Pago creo la preferencia {} sin back_urls.success: el deudor no va a "
                        + "volver solo. Revisa que PUBLIC_URL sea una direccion https.", id);
            }

            log.info("Preferencia Mercado Pago creada con éxito: id={}, initPoint={}", id, initPoint);
            return new Preferencia(id, initPoint, sandboxInitPoint);
        } catch (HttpClientErrorException e) {
            log.error("Mercado Pago rechazo la preferencia ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Mercado Pago rechazó la transacción. Verifica las credenciales.");
        } catch (RestClientException e) {
            log.error("No fue posible conectar con Mercado Pago", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Mercado Pago no responde en este momento. Prueba de nuevo o con otro medio.");
        }
    }

    /**
     * Busca los pagos hechos sobre la preferencia, para saber si ya se pago y
     * con que id.
     *
     * <p>Es la forma de conciliar un pago de Mercado Pago sin depender de la
     * vuelta del deudor ni del aviso: en local Mercado Pago no puede devolverse
     * a un {@code http://localhost} ni avisar a la maquina del desarrollador.
     * Los pagos no estan en la preferencia sino en sus ordenes, y se buscan por
     * el id de la preferencia, que es unico: un pago de otro cobro no aparece
     * aunque tenga la misma referencia.</p>
     *
     * <p>Si el deudor reintento (una tarjeta rechazada y despues otra), manda el
     * intento aprobado; si ninguno se aprobo, el ultimo.</p>
     *
     * @return lo que se sabe de la preferencia; sin pago mientras nadie la pague.
     */
    public EstadoPreferencia consultarPreferencia(String preferenceId) {
        try {
            JsonNode r = rest.get()
                    .uri(ORDENES + "?preference_id={id}", preferenceId)
                    .retrieve()
                    .body(JsonNode.class);

            if (r == null) {
                return null;
            }

            //  Sin pagos, Mercado Pago responde {"elements": null, "total": 0}.
            String extRef = null;
            Long total = null;
            PagoDePreferencia pago = null;
            for (JsonNode orden : r.path("elements")) {
                if (extRef == null && orden.hasNonNull("external_reference")) {
                    extRef = orden.get("external_reference").asText();
                }
                if (total == null && orden.hasNonNull("total_amount")) {
                    total = orden.get("total_amount").asLong();
                }
                for (JsonNode intento : orden.path("payments")) {
                    if (!intento.hasNonNull("id") || (pago != null && pago.pagado())) {
                        continue;
                    }
                    pago = new PagoDePreferencia(intento.get("id").asLong(),
                            intento.hasNonNull("status") ? intento.get("status").asText() : null,
                            intento);
                }
            }

            log.info("Preferencia Mercado Pago {} leida: extRef={}, total={}, pago={}",
                    preferenceId, extRef, total, pago != null ? pago.id() + "/" + pago.status() : "ninguno");
            return new EstadoPreferencia(preferenceId, extRef, total, pago);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("La preferencia {} no existe en Mercado Pago", preferenceId);
                return null;
            }
            log.error("Mercado Pago no pudo leer la preferencia {} ({}): {}",
                    preferenceId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la preferencia en Mercado Pago");
        } catch (RestClientException e) {
            log.error("Error al consultar la preferencia {} en Mercado Pago: {}", preferenceId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la preferencia en Mercado Pago");
        }
    }

    /**
     * Vence la preferencia ya: desde ahora Mercado Pago no deja pagarla. Se
     * usa cuando el deudor abre otro pago por la misma deuda, para que no
     * pague los dos. Si Mercado Pago no responde, devuelve false.
     */
    public boolean vencerPreferencia(String preferenceId) {
        String ahora = ZonedDateTime.now(ZoneId.of("America/Santiago")).truncatedTo(ChronoUnit.SECONDS).format(FECHA);
        try {
            rest.put()
                    .uri(PREFERENCIAS + "/{id}", preferenceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expires", true, "expiration_date_to", ahora))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Preferencia Mercado Pago {} vencida", preferenceId);
            return true;
        } catch (RestClientException e) {
            log.warn("No se pudo vencer la preferencia {} en Mercado Pago: {}", preferenceId, e.getMessage());
            return false;
        }
    }

    /**
     * Consulta el estado de un pago directamente en la API de Mercado Pago.
     */
    public PagoInfo consultarPago(String paymentId) {
        try {
            JsonNode r = rest.get()
                    .uri(PAGOS + "/{id}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);

            if (r == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Mercado Pago no respondió sobre el pago");
            }

            Long id = r.hasNonNull("id") ? r.get("id").asLong() : null;
            String status = r.hasNonNull("status") ? r.get("status").asText() : null;
            String statusDetail = r.hasNonNull("status_detail") ? r.get("status_detail").asText() : null;
            BigDecimal amount = r.hasNonNull("transaction_amount") ? new BigDecimal(r.get("transaction_amount").asText()) : null;
            String currency = r.hasNonNull("currency_id") ? r.get("currency_id").asText() : null;
            String extRef = r.hasNonNull("external_reference") ? r.get("external_reference").asText() : null;

            log.info("Consulta de pago Mercado Pago {}: status={}, amount={}", paymentId, status, amount);
            return new PagoInfo(id, status, statusDetail, amount, currency, extRef, r);
        } catch (RestClientException e) {
            log.error("Error al consultar pago {} en Mercado Pago: {}", paymentId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el estado del pago en Mercado Pago");
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CrearPreferencia(
            List<Item> items,
            Payer payer,
            BackUrls backUrls,
            String autoReturn,
            String externalReference,
            String statementDescriptor,
            Boolean expires,
            String expirationDateTo
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Item(String id, String title, String description, int quantity, long unitPrice, String currencyId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Payer(String email) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record BackUrls(String success, String failure, String pending) {}
}
