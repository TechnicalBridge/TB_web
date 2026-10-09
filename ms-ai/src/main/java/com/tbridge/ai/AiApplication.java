package com.tbridge.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ms-ai: el asistente del portal.
 *
 * <p>No tiene base de datos propia: consulta las deudas a ms-debt con la sesion
 * del deudor, asi que no puede ver nada que el deudor no pueda ver. Responde
 * con un LLM si hay uno configurado y, si no, con su motor local de reglas.
 */
@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
