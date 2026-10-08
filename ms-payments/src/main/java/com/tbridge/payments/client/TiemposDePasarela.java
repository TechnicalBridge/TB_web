package com.tbridge.payments.client;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Los tiempos maximos de las llamadas a las pasarelas y a ms-debt.
 *
 * <p>Sin ellos, una pasarela que no responde deja al deudor mirando una ventana
 * que no avanza y ocupa un hilo de ms-payments sin limite: con suficientes
 * cobros abiertos a la vez, el servicio entero deja de atender. Con ellos, la
 * llamada se corta y el deudor lee que pruebe de nuevo o con otro medio.
 */
public final class TiemposDePasarela {

    /** Para abrir la conexion: si en cinco segundos no contesta, no esta. */
    public static final Duration CONEXION = Duration.ofSeconds(5);

    /** Para la respuesta: las pasarelas tardan, pero no veinte segundos. */
    public static final Duration LECTURA = Duration.ofSeconds(20);

    private TiemposDePasarela() {
    }

    public static ClientHttpRequestFactory fabrica() {
        return fabrica(CONEXION, LECTURA);
    }

    public static ClientHttpRequestFactory fabrica(Duration conexion, Duration lectura) {
        //  HTTP/1.1: con HTTP/2 el cliente de Java ofrece subir de protocolo a
        //  los servidores http, y algunos no lo entienden.
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(conexion)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(lectura);
        return fabrica;
    }
}
