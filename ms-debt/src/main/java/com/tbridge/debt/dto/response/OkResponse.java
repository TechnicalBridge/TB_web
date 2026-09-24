package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Recibido. */
@Schema(description = "Recibido")
public record OkResponse(@Schema(example = "true") boolean ok) {

    public static final OkResponse OK = new OkResponse(true);
}
