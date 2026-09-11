package com.digitalbot.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanCatalogTest {

    @Test
    void find_returnsMatchingPlan() {
        PlanCatalog.Plan plan = PlanCatalog.find("profesional");

        assertNotNull(plan);
        assertEquals("profesional", plan.id());
        assertEquals("Profesional", plan.name());
        assertTrue(plan.popular());
    }

    @Test
    void find_returnsNullForUnknownPlan() {
        assertNull(PlanCatalog.find("no-existe"));
    }

    @Test
    void extras_returnsCatalogExtras() {
        List<PlanCatalog.Extra> extras = PlanCatalog.extras();

        assertEquals(3, extras.size());
        assertEquals("facturacion", extras.get(0).id());
        assertEquals("Módulo de facturación", extras.get(0).label());
        assertEquals(12, extras.get(0).monthly());
    }
}
