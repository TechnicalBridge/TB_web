package com.tbridge.debt.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cualquier direccion valida sirve; lo que no puede recibir correo, no. */
class CorreoTest {

    @Test
    void acepta_cualquier_direccion_valida() {
        for (String bueno : new String[]{"juan.perez+arriendo@gmail.com", "ana@sub.dominio.cl", "x@empresa.app",
                "maria_o'neil@correo.com", "contacto@xn--patrimonio-ninos-9qb.cl"}) {
            assertEquals(Optional.of(bueno), Correo.normalizar(bueno), bueno);
        }
    }

    @Test
    void rechaza_lo_que_no_puede_recibir_correo() {
        for (String malo : new String[]{"juan@gmail", "juan perez@gmail.com", "@gmail.com", "juan@", "juan",
                "juan@@gmail.com", "juan..perez@gmail.com", ".juan@gmail.com", "juan@-gmail.com",
                "juan@gmail.c", "juan@gmail.com.", "a".repeat(65) + "@gmail.com"}) {
            assertTrue(Correo.normalizar(malo).isEmpty(), malo);
        }
    }

    @Test
    void se_guarda_sin_espacios_alrededor_y_con_el_dominio_en_minusculas() {
        assertEquals(Optional.of("Juan.Perez@gmail.com"), Correo.normalizar("  Juan.Perez@GMAIL.Com "));
    }

    @Test
    void vacio_o_nulo_no_es_un_correo() {
        assertTrue(Correo.normalizar(null).isEmpty());
        assertTrue(Correo.normalizar("   ").isEmpty());
    }
}
