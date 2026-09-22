package com.tbridge.debt.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbridge.debt.domain.Batch;
import com.tbridge.debt.domain.Organization;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * El borde del contrato de integracion.
 *
 * <p>Se autentica con clave de API, no con la sesion del portal: quien entrega
 * cartera es un sistema, no una persona. Y el emisor sale de la clave, nunca
 * del cuerpo: un campo del cuerpo se puede falsificar, la clave no.
 */
@RestController
@RequestMapping("/api/v1")
public class IntegracionController {

    private final ApiKeyService claves;
    private final CarteraIntakeService carteras;
    private final MandatoService mandatos;
    private final EventosService eventos;

    public IntegracionController(ApiKeyService claves, CarteraIntakeService carteras,
                                 MandatoService mandatos, EventosService eventos) {
        this.claves = claves;
        this.carteras = carteras;
        this.mandatos = mandatos;
        this.eventos = eventos;
    }

    @PostMapping("/carteras")
    public Map<String, Object> carteras(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestBody JsonNode cuerpo
    ) {
        return carteras.recibir(emisor(autorizacion), cuerpo, Batch.Source.api);
    }

    /** Contrato 1, variante CSV (seccion 6.6): el mismo contrato en una planilla. */
    @PostMapping(value = "/carteras", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> carterasCsv(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("lote_id_externo") String loteId,
            @RequestParam("fecha_corte") String fechaCorte,
            @RequestParam("acreedor_rut") String acreedorRut,
            @RequestParam(value = "agencia_rut", required = false) String agenciaRut,
            @RequestParam(value = "campana_id_externo", required = false) String campana
    ) throws IOException {
        Organization emisor = emisor(autorizacion);
        CarteraCsv.Lote lote = new CarteraCsv.Lote(loteId, fechaCorte, acreedorRut, agenciaRut, campana);
        return carteras.recibir(emisor, CarteraCsv.leer(archivo.getBytes(), lote), Batch.Source.file);
    }

    @PostMapping("/mandatos")
    public Map<String, Object> mandatos(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestBody JsonNode cuerpo
    ) {
        return mandatos.registrarMandato(emisor(autorizacion), cuerpo);
    }

    @PostMapping("/campanas")
    public Map<String, Object> campanas(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestBody JsonNode cuerpo
    ) {
        return mandatos.registrarCampana(emisor(autorizacion), cuerpo);
    }

    /** Contrato 3: a donde avisarle los eventos a quien llama. */
    @PostMapping("/suscripciones")
    public Map<String, Object> suscripciones(
            @RequestHeader(value = "Authorization", required = false) String autorizacion,
            @RequestBody JsonNode cuerpo
    ) {
        return eventos.suscribir(emisor(autorizacion), cuerpo);
    }

    private Organization emisor(String autorizacion) {
        String clave = autorizacion != null && autorizacion.startsWith("Bearer ")
                ? autorizacion.substring(7).trim() : "";
        return claves.autenticar(clave).orElseThrow(() -> new CarteraInvalida(
                "no_autorizado", "Clave de API invalida", 401));
    }

    @ExceptionHandler(CarteraInvalida.class)
    public ResponseEntity<Map<String, Object>> alFallar(CarteraInvalida fallo) {
        return ResponseEntity.status(fallo.getStatus()).body(Map.of(
                "error", Map.of("codigo", fallo.getCodigo(), "mensaje", fallo.getMessage())));
    }
}
