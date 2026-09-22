package com.tbridge.debt.web;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.integracion.CargaService;
import com.tbridge.debt.integracion.CarteraInvalida;
import com.tbridge.debt.service.DebtService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * La carga de cartera por archivo desde el portal de empresas (drag & drop).
 * Quien carga es la organizacion de la sesion, nunca un RUT que venga en el
 * formulario.
 */
@RestController
public class CargaController {

    private final CargaService cargas;
    private final DebtService debts;

    public CargaController(CargaService cargas, DebtService debts) {
        this.cargas = cargas;
        this.debts = debts;
    }

    @GetMapping("/api/debts/cartera/opciones")
    public Map<String, Object> opciones(@AuthenticationPrincipal JwtPrincipal user) {
        return cargas.opciones(debts.organizacionDe(user));
    }

    @PostMapping(value = "/api/debts/cartera", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> subir(
            @AuthenticationPrincipal JwtPrincipal user,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("lote_id_externo") String loteId,
            @RequestParam("fecha_corte") String fechaCorte,
            @RequestParam("acreedor_rut") String acreedorRut,
            @RequestParam(value = "campana_id_externo", required = false) String campana
    ) throws IOException {
        return cargas.subir(debts.organizacionDe(user), archivo.getBytes(), loteId, fechaCorte, acreedorRut, campana);
    }

    @ExceptionHandler(CarteraInvalida.class)
    public ResponseEntity<Map<String, Object>> alFallar(CarteraInvalida fallo) {
        return ResponseEntity.status(fallo.getStatus()).body(Map.of(
                "error", Map.of("codigo", fallo.getCodigo(), "mensaje", fallo.getMessage())));
    }
}
