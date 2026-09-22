package com.tbridge.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbridge.payments.domain.UfValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La respuesta de GetSeries, con la forma que documenta el Banco Central. No
 * se probo contra el servicio real: pide credenciales.
 */
class BancoCentralUfTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void leeLosDiasPublicadosYSaltaLosQueNoTienenDato() throws IOException {
        String respuesta = """
                {"Codigo":0,"Descripcion":"Success",
                 "Series":{"descripEsp":"Unidad de fomento (UF)","seriesId":"F073.UFF.PRE.Z.D",
                   "Obs":[
                     {"indexDateString":"21-09-2026","value":"39801.23","statusCode":"OK"},
                     {"indexDateString":"22-09-2026","value":"39805.1","statusCode":"OK"},
                     {"indexDateString":"23-09-2026","value":"NaN","statusCode":"ND"}
                   ]},
                 "SeriesInfos":[]}
                """;
        List<UfValue> valores = BancoCentralUf.leer(respuesta, json);

        assertEquals(2, valores.size());
        assertEquals(LocalDate.of(2026, 9, 21), valores.get(0).getDay());
        assertEquals(new BigDecimal("39801.23"), valores.get(0).getValue());
        assertEquals(new BigDecimal("39805.10"), valores.get(1).getValue());
        assertEquals("bcentral", valores.get(0).getSource());
    }

    @Test
    void unErrorDelServicioNoSeConfundeConCeroDias() {
        String respuesta = """
                {"Codigo":-5,"Descripcion":"Invalid username or password","Series":{"Obs":[]}}
                """;
        IOException fallo = assertThrows(IOException.class, () -> BancoCentralUf.leer(respuesta, json));
        assertTrue(fallo.getMessage().contains("Invalid username"), fallo.getMessage());
    }
}
