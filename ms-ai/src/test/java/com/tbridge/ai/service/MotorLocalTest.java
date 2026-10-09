package com.tbridge.ai.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El motor local: responde sin LLM, con reglas. */
class MotorLocalTest {

    //  Tal como las entrega ms-debt (GET /api/debts).
    static final List<Map<String, Object>> DEUDAS = List.of(
            Map.of("acreedor", "Patrimonio Inmuebles", "concepto", "Arriendo mensual", "moneda", "CLP",
                    "montoOriginal", 1040000, "saldo", 1040000, "estado", "open"),
            Map.of("acreedor", "Patrimonio Inmuebles", "concepto", "Arriendo oficina", "moneda", "UF",
                    "montoOriginal", 115.5, "saldo", 96.25, "estado", "repacted"),
            Map.of("acreedor", "Patrimonio Inmuebles", "concepto", "Arriendo mensual", "moneda", "CLP",
                    "montoOriginal", 410000, "saldo", 0, "estado", "paid"));

    @Nested
    class LosMontos {

        @Test
        void pesos_y_uf_se_escriben_como_en_chile() {
            assertEquals("$1.040.000", MotorLocal.dinero(1040000));
            assertEquals("UF 96,25", MotorLocal.dinero(96.25, "UF"));
            assertEquals("UF 1.234,50", MotorLocal.dinero(1234.5, "UF"));
        }

        @Test
        void pesos_y_uf_no_se_suman() {
            assertEquals("$1.040.000 y UF 96,25", MotorLocal.totales(DEUDAS));
        }

        /** Leia remainingAmount, que ms-debt ya no manda: todo sumaba $0. */
        @Test
        void antes_decia_que_se_debian_cero_pesos() {
            String respuesta = MotorLocal.responder("cuánto debo?", DEUDAS);
            assertTrue(respuesta.contains("$1.040.000"), respuesta);
            assertTrue(respuesta.contains("UF 96,25"), respuesta);
            assertTrue(respuesta.contains("2 deuda(s) vigente(s)"), respuesta);
            assertTrue(respuesta.contains("1 ya está(n) pagada(s)"), respuesta);
        }
    }

    @Nested
    class ElSentimiento {

        @Test
        void frustracion() {
            assertEquals("frustracion", MotorLocal.sentimiento("estoy harto, esto es un abuso").etiqueta());
        }

        @Test
        void desconfianza_pesa_mas_que_la_cortesia() {
            assertEquals("desconfianza", MotorLocal.sentimiento("gracias, pero esto es una estafa?").etiqueta());
        }

        @Test
        void sin_tildes_ni_mayusculas() {
            assertEquals("frustracion", MotorLocal.sentimiento("NO TENGO PLATA").etiqueta());
        }

        @Test
        void neutral() {
            assertEquals("neutral", MotorLocal.sentimiento("cuánto debo").etiqueta());
        }
    }

    @Nested
    class ElTono {

        @Test
        void quien_desconfia_recibe_como_verificar() {
            String respuesta = MotorLocal.responder("¿esto es una estafa? cómo tienen mis datos", DEUDAS);
            assertTrue(respuesta.contains("nunca te manda un enlace"), respuesta);
            assertTrue(respuesta.contains("Patrimonio Inmuebles"), respuesta);
            assertFalse(respuesta.contains("$"), respuesta);
        }

        @Test
        void quien_no_puede_pagar_recibe_las_cuotas() {
            String respuesta = MotorLocal.responder("no tengo plata para pagar", DEUDAS);
            assertTrue(respuesta.startsWith("Entiendo que es una situación difícil."), respuesta);
            assertTrue(respuesta.contains("sin interés"), respuesta);
        }

        @Test
        void el_certificado_solo_para_lo_pagado() {
            assertTrue(MotorLocal.responder("quiero el certificado", DEUDAS).contains("1 deuda(s) pagada(s)"));
            assertTrue(MotorLocal.responder("certificado", DEUDAS.subList(0, 1)).contains("se emite cuando"));
        }
    }
}
