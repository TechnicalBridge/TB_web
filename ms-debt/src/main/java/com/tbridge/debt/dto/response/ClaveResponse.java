package com.tbridge.debt.dto.response;

import com.tbridge.debt.model.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;

/** Una clave de API, sin la clave: de ella solo queda el prefijo. */
@Relation(collectionRelation = "claves", itemRelation = "clave")
@Schema(description = "Una clave de API de la organizacion")
public record ClaveResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "Servidor de APOFYX") String nombre,
        @Schema(description = "Para reconocerla sin revelarla", example = "tbk_2x9Qa7Lm") String prefijo,
        Instant creadaEn,
        @Schema(nullable = true) Instant ultimoUso,
        @Schema(nullable = true) Instant revocadaEn,
        @Schema(example = "true") boolean activa
) {

    public static ClaveResponse from(ApiKey clave) {
        return new ClaveResponse(clave.getId(), clave.getName(), clave.getPrefix(), clave.getCreatedAt(),
                clave.getLastUsedAt(), clave.getRevokedAt(), clave.getRevokedAt() == null);
    }
}
