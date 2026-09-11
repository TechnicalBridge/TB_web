package com.digitalbot.domain;

import com.digitalbot.dto.ExtraLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtraListConverterTest {

    private final ExtraListConverter converter = new ExtraListConverter();

    @Test
    void convertToDatabaseColumn_serializesList() {
        List<ExtraLine> extras = List.of(
                new ExtraLine("facturacion", "Módulo de facturación", 12),
                new ExtraLine("sla", "SLA extendido", 20)
        );

        String json = converter.convertToDatabaseColumn(extras);

        assertNotNull(json);
        assertTrue(json.contains("\"id\":\"facturacion\""));
        assertTrue(json.contains("\"amount\":12"));
    }

    @Test
    void convertToEntityAttribute_deserializesJson() {
        String json = "[{\"id\":\"reportes\",\"label\":\"Reportes avanzados\",\"amount\":9}]";

        List<ExtraLine> extras = converter.convertToEntityAttribute(json);

        assertEquals(1, extras.size());
        assertEquals("reportes", extras.get(0).id());
        assertEquals("Reportes avanzados", extras.get(0).label());
        assertEquals(9, extras.get(0).amount());
    }

    @Test
    void convertToEntityAttribute_handlesNullAndBlank() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());
    }
}
