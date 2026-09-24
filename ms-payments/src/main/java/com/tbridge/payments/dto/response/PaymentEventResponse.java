package com.tbridge.payments.dto.response;

import com.tbridge.payments.model.PaymentEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Una linea del libro del pago. */
@Schema(description = "Un paso en la historia del pago")
public record PaymentEventResponse(
        @Schema(example = "paid") PaymentEvent.Type tipo,
        @Schema(example = "webhook") PaymentEvent.Source origen,
        @Schema(description = "Si la firma del aviso era valida. No viene si no hubo firma que revisar",
                nullable = true) Boolean firmaValida,
        Instant ocurrioEn
) {

    public static PaymentEventResponse from(PaymentEvent evento) {
        return new PaymentEventResponse(evento.getType(), evento.getSource(), evento.getSignatureOk(),
                evento.getOccurredAt());
    }
}
