package com.tbridge.debt.config;

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

    public static final String EXCHANGE = PagoConfirmado.EXCHANGE;
    public static final String QUEUE = PagoConfirmado.QUEUE;
    public static final String ROUTING_KEY = PagoConfirmado.ROUTING_KEY;

    @Bean
    public TopicExchange pagosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue pagoQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding pagoBinding(Queue pagoQueue, TopicExchange pagosExchange) {
        return BindingBuilder.bind(pagoQueue).to(pagosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
