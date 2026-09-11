package com.digitalbot.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanCatalog {

    private PlanCatalog() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Plan(
            String id,
            String name,
            String tagline,
            int monthly,
            int yearly,
            String accent,
            List<String> features,
            Boolean popular
    ) {}

    public record Extra(String id, String label, int monthly) {}

    public static final List<Plan> PLANS = List.of(
            new Plan(
                    "esencial",
                    "Esencial",
                    "Control claro para empezar",
                    19,
                    182,
                    "cyan",
                    List.of(
                            "Hasta 3 bots activos",
                            "Lista de pagos",
                            "Simulador de plan",
                            "Soporte estándar"
                    ),
                    null
            ),
            new Plan(
                    "profesional",
                    "Profesional",
                    "El más elegido por equipos",
                    49,
                    470,
                    "green",
                    List.of(
                            "Bots ilimitados",
                            "IA verificada DIGITAL BOT",
                            "Emisión de token de pago",
                            "Reportes automáticos",
                            "Prioridad 24/7"
                    ),
                    true
            ),
            new Plan(
                    "corporativo",
                    "Corporativo",
                    "Operación verificada a escala",
                    129,
                    1238,
                    "white",
                    List.of(
                            "Multi-equipo y roles",
                            "SLA verificado",
                            "API dedicada",
                            "Onboarding asistido",
                            "Auditoría de pagos"
                    ),
                    null
            )
    );

    public static final Map<String, Extra> EXTRAS = new LinkedHashMap<>();

    static {
        EXTRAS.put("facturacion", new Extra("facturacion", "Módulo de facturación", 12));
        EXTRAS.put("reportes", new Extra("reportes", "Reportes avanzados", 9));
        EXTRAS.put("sla", new Extra("sla", "SLA extendido", 20));
    }

    public static Plan find(String id) {
        if (id == null) {
            return null;
        }
        return PLANS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public static List<Extra> extras() {
        return List.copyOf(EXTRAS.values());
    }
}
