package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * El sobre de un evento del contrato 3 (Eventos v1): lo que viaja firmado a
 * quien entrego la cartera.
 *
 * <p>Lleva ids y montos, nunca datos personales (decision I5): el receptor ya
 * tiene la deuda y cruza por {@code deuda_id_externo}. {@code lote_id_externo}
 * no viene en los eventos que no nacen de un lote, como el avance de una campana.
 *
 * <p>{@code datos} es uno de los registros de este paquete, segun el tipo.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventoV1(
        String id,
        String tipo,
        String version,
        String ocurridoEn,
        String acreedorRut,
        String loteIdExterno,
        Object datos
) {
}
