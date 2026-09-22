package com.tbridge.payments.service;

import com.tbridge.common.events.PagoConfirmado;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Entrega un aviso a ms-debt.
 *
 * <p>No decide cuando ni reintenta: eso lo hace el despachador de la bandeja
 * de salida. Aca solo se intenta una vez y se deja que la excepcion suba, que
 * es lo que el despachador necesita para programar el proximo intento.
 */
@Service
public class EventPublisher {

    private final RabbitTemplate rabbit;
    private final RestClient rest;
    private final String debtUrl;
    private final String internalKey;
    private final boolean rabbitEnabled;

    public EventPublisher(
            ObjectProvider<RabbitTemplate> rabbit,
            @Value("${app.debt-url}") String debtUrl,
            @Value("${app.internal-key}") String internalKey,
            @Value("${events.rabbit:false}") boolean rabbitEnabled
    ) {
        this.rabbit = rabbit.getIfAvailable();
        this.rest = RestClient.create();
        this.debtUrl = debtUrl.replaceAll("/$", "");
        this.internalKey = internalKey;
        this.rabbitEnabled = rabbitEnabled && this.rabbit != null;
    }

    /** Lanza si no se pudo entregar. El que llama decide que hacer. */
    public void publicar(PagoConfirmado aviso) {
        if (rabbitEnabled) {
            rabbit.convertAndSend(PagoConfirmado.EXCHANGE, PagoConfirmado.ROUTING_KEY, aviso);
            return;
        }
        rest.post()
                .uri(debtUrl + "/internal/events/pago-confirmado")
                .header("X-Internal-Key", internalKey)
                .body(aviso)
                .retrieve()
                .toBodilessEntity();
    }
}
