package com.tbridge.debt.web;

import com.tbridge.common.jwt.JwtPrincipal;
import com.tbridge.common.web.ApiException;
import com.tbridge.debt.repo.OrganizationRepository;
import com.tbridge.debt.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final OrganizationRepository organizations;

    public AnalyticsController(AnalyticsService analytics, OrganizationRepository organizations) {
        this.analytics = analytics;
        this.organizations = organizations;
    }

    /** El resumen de la cartera de quien pregunta, y de nadie mas. */
    @GetMapping("/api/analytics/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal JwtPrincipal user) {
        if (user == null || !user.isCreditor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo el acreedor ve el resumen");
        }
        if (user.rut() == null || user.rut().isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "El token no dice de que empresa eres");
        }
        return analytics.resumen(organizations.findByRut(user.rut())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "Esa empresa no esta registrada en DataBridge")));
    }
}
