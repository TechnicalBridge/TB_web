package com.tbridge.payments.service;

import com.tbridge.common.events.PagoExitosoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EventPublisher {

    public static final String EXCHANGE = "tbridge.pagos";
    public static final String ROUTING_KEY = "pago.exitoso";

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

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

    public void publishPagoExitoso(PagoExitosoEvent event) {
        if (rabbitEnabled) {
            try {
                rabbit.convertAndSend(EXCHANGE, ROUTING_KEY, event);
                log.info("Evento pago_exitoso publicado en RabbitMQ payment={}", event.paymentId());
                return;
            } catch (Exception e) {
                log.warn("RabbitMQ no disponible, fallback HTTP: {}", e.getMessage());
            }
        }
        rest.post()
                .uri(debtUrl + "/internal/events/pago-exitoso")
                .header("X-Internal-Key", internalKey)
                .body(event)
                .retrieve()
                .toBodilessEntity();
        log.info("Evento pago_exitoso enviado por HTTP a MS-Debt payment={}", event.paymentId());
    }
}
