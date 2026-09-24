package com.tbridge.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * El gateway: la unica puerta hacia los servicios.
 *
 * <p>Reparte cada peticion por su ruta (config/RutasConfig), limita cuantas
 * puede hacer cada cliente (filter/RateLimitFilter) y publica en una sola
 * pagina la documentacion de todos los servicios. No verifica el JWT: eso lo
 * hace cada servicio por su cuenta, sin confiar en que otro ya lo reviso.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
