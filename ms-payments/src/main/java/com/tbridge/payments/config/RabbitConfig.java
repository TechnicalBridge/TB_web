package com.tbridge.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbridge.common.events.PagoConfirmado;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "events.rabbit", havingValue = "true")
public class RabbitConfig {

    @Bean
    public TopicExchange pagosExchange() {
        return new TopicExchange(PagoConfirmado.EXCHANGE, true, false);
    }

    /*
     * La cola se declara tambien de este lado, con el mismo nombre y la misma
     * clave que en ms-debt. Si solo la declarara ms-debt, un pago publicado
     * antes de que ms-debt arranque por primera vez no tendria donde caer y
     * RabbitMQ lo descartaria. Declararla dos veces es inocuo: es la misma.
     */
    @Bean
    public Queue pagosConfirmados() {
        return new Queue(PagoConfirmado.QUEUE, true);
    }

    @Bean
    public Binding pagosConfirmadosBinding(Queue pagosConfirmados, TopicExchange pagosExchange) {
        return BindingBuilder.bind(pagosConfirmados).to(pagosExchange).with(PagoConfirmado.ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
