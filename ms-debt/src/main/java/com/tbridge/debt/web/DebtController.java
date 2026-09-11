package com.tbridge.debt.web;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.service.CertificateService;
import com.tbridge.debt.service.CsvIngestService;
import com.tbridge.debt.service.DebtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class DebtController {

    private final DebtService debts;
    private final CsvIngestService csv;
    private final CertificateService certificates;

    public DebtController(DebtService debts, CsvIngestService csv, CertificateService certificates) {
        this.debts = debts;
        this.csv = csv;
        this.certificates = certificates;
    }

    @GetMapping("/api/debts")
    public Map<String, Object> list(@AuthenticationPrincipal JwtPrincipal user) {
        return Map.of("debts", debts.listFor(user));
    }

    @GetMapping("/api/debts/{id}")
    public Map<String, Object> one(@AuthenticationPrincipal JwtPrincipal user, @PathVariable String id) {
        return debts.getFor(user, id);
    }

    @GetMapping("/api/debts/{id}/simulate")
    public Map<String, Object> simulate(
            @AuthenticationPrincipal JwtPrincipal user,
            @PathVariable String id,
            @RequestParam(defaultValue = "12") int months
    ) {
        return Map.of("plan", debts.simulate(user, id, months));
    }

    @PostMapping("/api/debts/{id}/repact")
    public Map<String, Object> repact(
            @AuthenticationPrincipal JwtPrincipal user,
            @PathVariable String id,
            @RequestBody Map<String, Integer> body
    ) {
        int months = body.getOrDefault("months", 12);
        return debts.applyRepact(user, id, months);
    }

    @GetMapping("/api/debts/{id}/certificate")
    public ResponseEntity<byte[]> certificate(@AuthenticationPrincipal JwtPrincipal user, @PathVariable String id) {
        byte[] pdf = certificates.generate(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificado-deuda-cero.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/api/debts/ingest")
    public Map<String, Object> ingest(
            @AuthenticationPrincipal JwtPrincipal user,
            @RequestParam("file") MultipartFile file
    ) {
        if (user == null || !user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo DataBridge puede cargar cartera");
        }
        String creditor = user.name() == null || user.name().isBlank() ? "DataBridge" : user.name();
        return csv.ingest(file, creditor);
    }
}
