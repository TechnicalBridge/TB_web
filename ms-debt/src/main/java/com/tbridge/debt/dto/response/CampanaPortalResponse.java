package com.tbridge.debt.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Una campana, como la ve en el portal la empresa que la gestiona, con su avance. */
@Relation(collectionRelation = "campanas", itemRelation = "campana")
@Schema(description = "Una campana de la empresa, con su avance")
public record CampanaPortalResponse(
        @Schema(example = "APX-CMP-8") String idExterno,
        @Schema(example = "Arriendos octubre") String nombre,
        @Schema(example = "76418902-7") String acreedorRut,
        @Schema(description = "El nombre del acreedor", example = "Patrimonio Inmuebles") String acreedor,
        @Schema(description = "La campana es del mismo acreedor, que cobra sin agencia", example = "false") boolean propia,
        @Schema(example = "2026-10-01") LocalDate inicio,
        @Schema(nullable = true, example = "2026-11-30") LocalDate fin,
        @Schema(example = "[\"correo\"]") List<String> canales,
        @Schema(example = "3") int intentos,
        @Schema(description = "El dia de cada toque, desde la entrada de la deuda", example = "[1, 4, 11]")
        List<Integer> cadenciaDias,
        @Schema(allowableValues = {"en_curso", "pausada", "terminada"}, example = "en_curso") String estado,
        @Schema(description = "Las deudas de la campana", example = "12") int deudas,
        @Schema(description = "Los correos que salieron: invitaciones y toques", example = "20") int contactos,
        @Schema(description = "Los pagos recibidos", example = "4") int pagos,
        @Schema(description = "Las deudas saldadas", example = "3") int saldadas,
        @Schema(description = "El % de los intereses de mora que condona por tramo (1-30, 31-90 y 91-120). Vacio "
                + "sin descuento", nullable = true, example = "{\"31-90\": 50, \"91-120\": 100}")
        Map<String, BigDecimal> descuentoPorTramo
) {

    /** Una campana sin descuento por pronto pago. */
    public CampanaPortalResponse(String idExterno, String nombre, String acreedorRut, String acreedor, boolean propia,
                                 LocalDate inicio, LocalDate fin, List<String> canales, int intentos,
                                 List<Integer> cadenciaDias, String estado, int deudas, int contactos, int pagos,
                                 int saldadas) {
        this(idExterno, nombre, acreedorRut, acreedor, propia, inicio, fin, canales, intentos, cadenciaDias, estado,
                deudas, contactos, pagos, saldadas, null);
    }
}
