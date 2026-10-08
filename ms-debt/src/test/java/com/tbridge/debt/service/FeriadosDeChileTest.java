package com.tbridge.debt.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los feriados calculados, contra los publicados. Cada fecha esperada se
 * verifico antes de escribirla: feriados.cl (2026 y 2027), la prensa de cada
 * ano (2018, 2019, 2021 a 2025) y las horas del solsticio de la tabla de
 * Wikipedia ("June solstice") y de timedate.org (2027).
 */
class FeriadosDeChileTest {

    private static Set<LocalDate> dias(int ano, String... mesDia) {
        return Stream.of(mesDia).map(md -> LocalDate.parse(ano + "-" + md)).collect(Collectors.toSet());
    }

    private static void tiene(int ano, String mesDia, String por) {
        assertTrue(FeriadosDeChile.delAno(ano).contains(LocalDate.parse(ano + "-" + mesDia)), por);
    }

    private static void noTiene(int ano, String mesDia, String por) {
        assertFalse(FeriadosDeChile.delAno(ano).contains(LocalDate.parse(ano + "-" + mesDia)), por);
    }

    @Test
    void da_las_listas_oficiales_completas() {
        assertEquals(dias(2023, "01-01", "01-02", "04-07", "04-08", "05-01", "05-21", "06-21", "06-26",
                        "07-16", "08-15", "09-18", "09-19", "10-09", "10-27", "11-01", "12-08", "12-25"),
                FeriadosDeChile.delAno(2023), "2023, con el 2 de enero y el viernes 27 de octubre");
        assertEquals(dias(2026, "01-01", "04-03", "04-04", "05-01", "05-21", "06-21", "06-29", "07-16",
                        "08-15", "09-18", "09-19", "10-12", "10-31", "11-01", "12-08", "12-25"),
                FeriadosDeChile.delAno(2026), "2026");
        assertEquals(dias(2027, "01-01", "03-26", "03-27", "05-01", "05-21", "06-21", "06-28", "07-16",
                        "08-15", "09-17", "09-18", "09-19", "10-11", "10-31", "11-01", "12-08", "12-25"),
                FeriadosDeChile.delAno(2027), "2027, con el viernes 17 de septiembre");
    }

    @Test
    void ano_nuevo_un_domingo_suma_el_lunes_2() {
        tiene(2023, "01-02", "el 1 de enero de 2023 fue domingo");
        noTiene(2026, "01-02", "el de 2026 fue jueves");
    }

    @Test
    void fiestas_patrias_se_unen_con_el_fin_de_semana() {
        tiene(2018, "09-17", "2018: el 18 fue martes, el lunes 17 es feriado (Ley 20.215)");
        tiene(2019, "09-20", "2019: el 18 fue miercoles, el viernes 20 es feriado (Ley 20.215)");
        tiene(2024, "09-20", "2024: igual que 2019");
        tiene(2021, "09-17", "2021: el 18 fue sabado, el viernes 17 es feriado (Ley 20.983)");
        tiene(2027, "09-17", "2027: igual que 2021");
        noTiene(2025, "09-17", "2025: el 18 fue jueves y el 19 viernes, no se agrega nada");
        noTiene(2025, "09-20", "2025: el 20 cayo sabado");
    }

    @Test
    void san_pedro_y_el_encuentro_de_dos_mundos_se_corren_al_lunes() {
        tiene(2022, "06-27", "2022: el 29 fue miercoles");
        tiene(2023, "06-26", "2023: el 29 fue jueves");
        tiene(2027, "06-28", "2027: el 29 cae martes");
        tiene(2022, "10-10", "2022: el 12 fue miercoles");
        tiene(2023, "10-09", "2023: el 12 fue jueves");
        tiene(2024, "10-12", "2024: el 12 fue sabado y no se movio");
        noTiene(2024, "10-14", "ni al lunes siguiente");
        tiene(2025, "06-29", "2025: el 29 fue domingo y no se movio");
        assertEquals(LocalDate.of(2029, 7, 2), FeriadosDeChile.alLunes(LocalDate.of(2029, 6, 29)),
                "un viernes pasa al lunes siguiente");
    }

    @Test
    void el_31_de_octubre_un_martes_o_un_miercoles_pasa_a_un_viernes() {
        tiene(2023, "10-27", "2023: el 31 fue martes, pasa al viernes anterior");
        noTiene(2023, "10-31", "y el martes deja de serlo");
        tiene(2018, "11-02", "2018: el 31 fue miercoles, pasa al viernes siguiente");
        tiene(2022, "10-31", "2022: el 31 fue lunes y se queda");
    }

    @Test
    void pueblos_indigenas_es_el_dia_del_solsticio_en_hora_de_chile() {
        tiene(2022, "06-21", "2022");
        tiene(2023, "06-21", "2023");
        tiene(2024, "06-20", "2024: el solsticio fue el 20 a las 20:51 UTC");
        tiene(2025, "06-20", "2025: a las 02:42 UTC del 21, que en Chile eran las 22:42 del 20");
        noTiene(2025, "06-21", "por eso el 21 no");
        tiene(2026, "06-21", "2026");
        tiene(2027, "06-21", "2027: a las 14:11 UTC del 21");
        tiene(2021, "06-21", "2021, su primer ano, lo fijo la Ley 21.357");
    }

    @Test
    void el_solsticio_al_minuto() {
        assertCerca("2022-06-21T09:14:00Z", FeriadosDeChile.solsticioDeInvierno(2022));
        assertCerca("2023-06-21T14:58:00Z", FeriadosDeChile.solsticioDeInvierno(2023));
        assertCerca("2024-06-20T20:51:00Z", FeriadosDeChile.solsticioDeInvierno(2024));
        assertCerca("2025-06-21T02:42:00Z", FeriadosDeChile.solsticioDeInvierno(2025));
        assertCerca("2026-06-21T08:25:00Z", FeriadosDeChile.solsticioDeInvierno(2026));
        assertCerca("2027-06-21T14:11:00Z", FeriadosDeChile.solsticioDeInvierno(2027));
    }

    private static void assertCerca(String publicado, Instant calculado) {
        Duration diferencia = Duration.between(Instant.parse(publicado), calculado).abs();
        assertTrue(diferencia.compareTo(Duration.ofMinutes(2)) <= 0,
                "publicado " + publicado + ", calculado " + calculado);
    }

    @Test
    void la_pascua() {
        assertEquals(LocalDate.of(2008, 3, 23), FeriadosDeChile.pascua(2008), "una de las mas tempranas");
        assertEquals(LocalDate.of(2019, 4, 21), FeriadosDeChile.pascua(2019));
        assertEquals(LocalDate.of(2024, 3, 31), FeriadosDeChile.pascua(2024));
        assertEquals(LocalDate.of(2025, 4, 20), FeriadosDeChile.pascua(2025));
        assertEquals(LocalDate.of(2027, 3, 28), FeriadosDeChile.pascua(2027));
        assertEquals(LocalDate.of(2038, 4, 25), FeriadosDeChile.pascua(2038), "la mas tardia posible");
    }

    @Test
    void lo_que_se_decreta_no_se_inventa() {
        noTiene(2022, "09-16", "el viernes 16 de septiembre de 2022 fue un feriado especial: va en CONTACTO_FERIADOS");
    }
}
