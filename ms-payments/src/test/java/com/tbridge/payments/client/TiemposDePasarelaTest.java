package com.tbridge.payments.client;

import com.sun.net.httpserver.HttpServer;
import com.tbridge.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una pasarela que no responde no deja al deudor esperando: cada cliente corta
 * a los segundos, con un mensaje que no muestra el detalle de la pasarela.
 *
 * <p>Con un servidor local que tarda mas que el tiempo maximo, y con un puerto
 * donde no escucha nadie. Las pruebas usan tiempos cortos para no esperar
 * veinte segundos: lo que se prueba es que el tiempo maximo se aplica.
 */
class TiemposDePasarelaTest {

    private static final Duration TARDA = Duration.ofSeconds(3);

    private HttpServer lenta;

    @BeforeEach
    void levantar() throws IOException {
        lenta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lenta.setExecutor(Executors.newCachedThreadPool());
        lenta.createContext("/", intercambio -> {
            try {
                Thread.sleep(TARDA.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        lenta.start();
    }

    @AfterEach
    void bajar() {
        lenta.stop(0);
    }

    private String lentaUrl() {
        return "http://127.0.0.1:" + lenta.getAddress().getPort();
    }

    /** Un puerto donde no escucha nadie: la pasarela caida. */
    private static String caidaUrl() throws IOException {
        try (ServerSocket libre = new ServerSocket(0)) {
            return "http://127.0.0.1:" + libre.getLocalPort();
        }
    }

    private static ClientHttpRequestFactory corta() {
        return TiemposDePasarela.fabrica(Duration.ofSeconds(1), Duration.ofMillis(300));
    }

    private static void cortaPronto(Executable llamada, HttpStatus esperado) {
        long inicio = System.nanoTime();
        ApiException error = assertThrows(ApiException.class, llamada);
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertEquals(esperado, error.getStatus());
        assertTrue(ms < 2_000, "corto a los " + ms + " ms en vez de esperar a la pasarela");
        assertFalse(error.getMessage().toLowerCase().contains("timeout"), "el deudor no lee el detalle tecnico");
        assertFalse(error.getMessage().contains("127.0.0.1"), "ni la direccion de la pasarela");
    }

    @Test
    void webpay_corta_si_transbank_no_responde_o_esta_caido() throws IOException {
        WebpayClient lento = new WebpayClient(lentaUrl(), "597055555532", "llave", "TEST", "", corta());
        cortaPronto(() -> lento.createTransaction("ORD41T1", "sesion", 1000, "http://localhost/vuelta"), HttpStatus.BAD_GATEWAY);
        cortaPronto(() -> lento.commitTransaction("tok"), HttpStatus.BAD_GATEWAY);
        cortaPronto(() -> lento.estado("tok"), HttpStatus.BAD_GATEWAY);

        WebpayClient caido = new WebpayClient(caidaUrl(), "597055555532", "llave", "TEST", "", corta());
        cortaPronto(() -> caido.estado("tok"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void khipu_corta_si_no_responde_o_esta_caido() throws IOException {
        cortaPronto(() -> new KhipuClient(lentaUrl(), "llave", corta()).estado("pago"), HttpStatus.BAD_GATEWAY);
        cortaPronto(() -> new KhipuClient(caidaUrl(), "llave", corta()).estado("pago"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mercado_pago_corta_si_no_responde_o_esta_caido() throws IOException {
        MercadoPagoClient lento = new MercadoPagoClient(lentaUrl(), "TEST-token", "TEST-pk", "TEST", corta());
        cortaPronto(() -> lento.consultarPago("123"), HttpStatus.BAD_GATEWAY);
        cortaPronto(() -> lento.consultarPreferencia("pref"), HttpStatus.BAD_GATEWAY);
        cortaPronto(() -> new MercadoPagoClient(caidaUrl(), "TEST-token", "TEST-pk", "TEST", corta())
                .consultarPago("123"), HttpStatus.BAD_GATEWAY);
    }

    @Test
    void ms_debt_corta_si_no_responde_y_el_deudor_lee_que_pruebe_mas_tarde() throws IOException {
        cortaPronto(() -> new DebtClient(lentaUrl(), "clave", corta()).obtener(3L, null), HttpStatus.SERVICE_UNAVAILABLE);
        cortaPronto(() -> new DebtClient(caidaUrl(), "clave", corta()).obtener(3L, null), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void por_omision_cinco_segundos_para_conectar_y_veinte_para_responder() {
        assertEquals(Duration.ofSeconds(5), TiemposDePasarela.CONEXION);
        assertEquals(Duration.ofSeconds(20), TiemposDePasarela.LECTURA);
    }
}
