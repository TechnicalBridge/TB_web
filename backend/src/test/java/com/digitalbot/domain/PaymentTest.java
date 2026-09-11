package com.digitalbot.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void settersAndGetters_roundTripValues() {
        Payment payment = new Payment();
        Instant createdAt = Instant.parse("2025-03-10T11:30:00Z");

        payment.setId("pay-1");
        payment.setUserId("user-1");
        payment.setPlanId("profesional");
        payment.setAmount(4900);
        payment.setCurrency("EUR");
        payment.setMethod("card");
        payment.setStatus("paid");
        payment.setCreatedAt(createdAt);
        payment.setLast4("4242");
        payment.setReference("ref-42");
        payment.setBilling("invoice");
        payment.setTokenCode("DBT-1234");

        assertEquals("pay-1", payment.getId());
        assertEquals("user-1", payment.getUserId());
        assertEquals("profesional", payment.getPlanId());
        assertEquals(4900, payment.getAmount());
        assertEquals("EUR", payment.getCurrency());
        assertEquals("card", payment.getMethod());
        assertEquals("paid", payment.getStatus());
        assertEquals(createdAt, payment.getCreatedAt());
        assertEquals("4242", payment.getLast4());
        assertEquals("ref-42", payment.getReference());
        assertEquals("invoice", payment.getBilling());
        assertEquals("DBT-1234", payment.getTokenCode());
    }
}
