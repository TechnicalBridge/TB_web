package com.tbridge.debt.web;

import com.tbridge.common.events.PagoConfirmado;
import com.tbridge.common.util.Rut;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.integracion.ApiKeyService;
import com.tbridge.debt.repo.OrganizationRepository;
import com.tbridge.debt.service.DebtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lo que un servicio hermano le pregunta o le avisa a ms-debt.
 *
 * <p>No es publico: va detras de una clave interna y el gateway no lo expone.
 */
@RestController
public class InternalController {

    private final DebtService debts;
    private final ApiKeyService claves;
    private final OrganizationRepository organizations;
    private final String internalKey;

    public InternalController(DebtService debts, ApiKeyService claves,
                              OrganizationRepository organizations,
                              @Value("${app.internal-key}") String internalKey) {
        this.debts = debts;
        this.claves = claves;
        this.organizations = organizations;
        this.internalKey = internalKey;
    }

    private void exigirClave(String clave) {
        if (clave == null || !clave.equals(internalKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave interna invalida");
        }
    }

    /**
     * Cuanto se debe y a quien.
     *
     * <p>Lo usa ms-payments para no aceptar el monto del navegador: quien
     * supiera el id de una deuda podia pagar un peso y darla por saldada.
     */
    @GetMapping("/internal/debts/{id}")
    public Map<String, Object> deuda(
            @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @PathVariable Long id,
            @RequestParam(required = false) Long installmentId
    ) {
        exigirClave(clave);
        return debts.snapshotInterno(id, installmentId);
    }

    /**
     * Emite una clave de API para una organizacion.
     *
     * <p>La clave se devuelve UNA vez: en la base queda solo su huella, asi
     * que despues de esta respuesta no existe en ninguna parte.
     */
    @PostMapping("/internal/claves")
    public Map<String, Object> clave(
            @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @RequestBody Map<String, String> body
    ) {
        exigirClave(clave);
        String rut = Rut.normalizar(body.get("rut"));
        var organizacion = organizations.findByRut(rut).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "No hay ninguna organizacion con RUT " + rut));
        var emitida = claves.emitir(organizacion, body.getOrDefault("nombre", "sin nombre"));
        return Map.of(
                "clave", emitida.clave(),
                "prefijo", emitida.registro().getPrefix(),
                "organizacion", organizacion.getTradeName(),
                "aviso", "Guardala ahora: no se puede volver a mostrar");
    }

    /** El aviso de un pago concretado. */
    @PostMapping("/internal/events/pago-confirmado")
    public Map<String, Object> pagoConfirmado(
            @RequestHeader(value = "X-Internal-Key", required = false) String clave,
            @RequestBody PagoConfirmado aviso
    ) {
        exigirClave(clave);
        debts.onPagoConfirmado(aviso);
        return Map.of("ok", true);
    }
}
