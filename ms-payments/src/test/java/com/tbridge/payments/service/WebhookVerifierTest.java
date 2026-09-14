package com.tbridge.payments.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookVerifierTest {

    private final WebhookVerifier verifier = new WebhookVerifier("unit-webhook-secret");

    @Test
    void acceptsMatchingSignature() {
        String sig = verifier.sign("pay-1", "150000", "debt-9");
        assertTrue(verifier.matches(sig, "pay-1", "150000", "debt-9"));
        assertTrue(verifier.matches(verifier.sign("pay-1", "150000.00", "debt-9"), "pay-1", "150000.0", "debt-9"));
    }

    @Test
    void rejectsTamperedPayload() {
        String sig = verifier.sign("pay-1", "150000", "debt-9");
        assertFalse(verifier.matches(sig, "pay-1", "150001", "debt-9"));
        assertFalse(verifier.matches("deadbeef", "pay-1", "150000", "debt-9"));
    }
}
