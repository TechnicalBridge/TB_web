package com.tbridge.payments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** El libro de un pago: como llego a estar donde esta. */
@Schema(description = "Los pasos del pago, del primero al ultimo")
public record HistoriaResponse(List<PaymentEventResponse> eventos) {
}
