package com.tbridge.common.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PagoExitosoEvent(
        String event,
        String paymentId,
        String debtId,
        String installmentId,
        BigDecimal amount,
        String gateway,
        String debtorEmail,
        Instant paidAt
) {
}
