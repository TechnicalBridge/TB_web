package com.tbridge.debt.web;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.debt.service.AccesoService;
import com.tbridge.debt.service.CertificateService;
import com.tbridge.debt.service.DebtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Las deudas del portal.
 *
 * <p>La carga de cartera ya no vive aqui: entra por el contrato de
 * integracion, en /api/v1/carteras, con su propia autenticacion.
 */
@RestController
public class DebtController {

    private final DebtService debts;
    private final CertificateService certificates;
    private final AccesoService acceso;

    public DebtController(DebtService debts, CertificateService certificates, AccesoService acceso) {
        this.debts = debts;
        this.certificates = certificates;
        this.acceso = acceso;
    }

    @GetMapping("/api/debts")
    public Map<String, Object> list(@AuthenticationPrincipal JwtPrincipal user) {
        return Map.of("debts", debts.listFor(user));
    }

    @GetMapping("/api/debts/{id}")
    public Map<String, Object> one(@AuthenticationPrincipal JwtPrincipal user, @PathVariable Long id) {
        return debts.getFor(user, id);
    }

    @GetMapping("/api/debts/{id}/simulate")
    public Map<String, Object> simulate(
            @AuthenticationPrincipal JwtPrincipal user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "12") int months
    ) {
        return Map.of("plan", debts.simulate(user, id, months));
    }

    @PostMapping("/api/debts/{id}/repact")
    public Map<String, Object> repact(
            @AuthenticationPrincipal JwtPrincipal user,
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body
    ) {
        return debts.applyRepact(user, id, body.getOrDefault("months", 12));
    }

    /** El acreedor le hace llegar al deudor su codigo de acceso, al correo. */
    @PostMapping("/api/debts/{id}/codigo")
    public Map<String, Object> codigo(@AuthenticationPrincipal JwtPrincipal user, @PathVariable Long id) {
        return acceso.enviarCodigo(user, id);
    }

    @GetMapping("/api/debts/{id}/certificate")
    public ResponseEntity<byte[]> certificate(
            @AuthenticationPrincipal JwtPrincipal user, @PathVariable Long id) {
        byte[] pdf = certificates.generate(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"certificado-deuda-cero.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
