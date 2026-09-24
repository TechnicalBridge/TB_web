package com.tbridge.debt.dto.evento;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * {@code lote.procesado}: como quedo un lote, para quien lo entrego. Cuantas
 * entraron y como se reparte la mora.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LoteProcesadoDatos(
        String periodo,
        String fechaCorte,
        Integer recibidas,
        Integer aceptadas,
        Integer rechazadas,
        List<Tramo> tramos
) {

    /**
     * Un tramo de mora. El promedio va solo sobre las deudas en pesos, y no
     * viene si el tramo no tiene ninguna: una media que mezclara pesos con UF
     * no significaria nada.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Tramo(String tramo, int deudas, Long promedioClp) {
    }
}
