package com.tbridge.debt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbridge.debt.events.PagoConfirmado;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La cola de avisos de pago, solo si EVENTS_RABBIT=true.
 *
 * <p>Los nombres salen de {@link PagoConfirmado}, igual que en ms-payments:
 * cuando cada servicio tenia los suyos, no calzaban y RabbitMQ descartaba cada
 * pago en silencio.
 */
@Configuration
@ConditionalOnProperty(name = "events.rabbit", havingValue = "true")
public class RabbitConfig {

    @Bean
    public TopicExchange pagosExchange() {
        return new TopicExchange(PagoConfirmado.EXCHANGE, true, false);
    }

    @Bean
    public Queue pagoQueue() {
        return new Queue(PagoConfirmado.QUEUE, true);
    }

    @Bean
    public Binding pagoBinding(Queue pagoQueue, TopicExchange pagosExchange) {
        return BindingBuilder.bind(pagoQueue).to(pagosExchange).with(PagoConfirmado.ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonMessageConverter convertidor = new Jackson2JsonMessageConverter(mapper);
        //  El aviso se lee con la clase del listener, nunca con la que dice el
        //  mensaje: ms-payments tiene su propia clase, en su propio paquete, y
        //  un mensaje no puede elegir que clase se crea aca.
        convertidor.setAlwaysConvertToInferredType(true);
        return convertidor;
    }
}
