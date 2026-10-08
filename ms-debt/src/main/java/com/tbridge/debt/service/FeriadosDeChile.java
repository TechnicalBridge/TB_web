package com.tbridge.debt.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;

/**
 * Los feriados nacionales de Chile de cualquier ano, calculados con las reglas
 * de la ley. Las campanas no contactan esos dias (Ley 19.496, art. 37).
 *
 * <p>Lo que no se puede calcular —elecciones, plebiscitos y feriados que se
 * decretan para una ocasion— se suma con {@code CONTACTO_FERIADOS}. Los
 * regionales (Arica y Parinacota, Chillan y Chillan Viejo) no estan: DataBridge
 * no sabe en que region vive el deudor.
 *
 * <p>Las reglas valen desde 2021, el ano del ultimo feriado permanente que se
 * creo (Pueblos Indigenas, Ley 21.357). DataBridge solo necesita el ano en
 * curso y los que vienen.
 */
final class FeriadosDeChile {

    /** La hora oficial de Chile continental: decide en que dia cae el solsticio. */
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    /**
     * TT − UT: el solsticio se calcula en tiempo dinamico y la hora civil va
     * en tiempo universal. Son unos 69 segundos hoy y cambia menos de un
     * segundo por ano, asi que un valor fijo basta para saber el dia.
     */
    private static final double DELTA_T_SEGUNDOS = 69;

    /** Los 24 terminos periodicos del solsticio (Meeus, tabla 27.C): A, B en grados, C en grados por siglo. */
    private static final double[][] TERMINOS = {
            {485, 324.96, 1934.136}, {203, 337.23, 32964.467}, {199, 342.08, 20.186},
            {182, 27.85, 445267.112}, {156, 73.14, 45036.886}, {136, 171.52, 22518.443},
            {77, 222.54, 65928.934}, {74, 296.72, 3034.906}, {70, 243.58, 9037.513},
            {58, 119.81, 33718.147}, {52, 297.17, 150.678}, {50, 21.02, 2281.226},
            {45, 247.54, 29929.562}, {44, 325.15, 31555.956}, {29, 60.93, 4443.417},
            {18, 155.12, 67555.328}, {17, 288.79, 4562.452}, {16, 198.04, 62894.029},
            {14, 199.76, 31436.921}, {12, 95.39, 14577.848}, {12, 287.11, 31931.756},
            {12, 320.81, 34777.259}, {9, 227.73, 1222.114}, {8, 15.45, 16859.074},
    };

    private FeriadosDeChile() {
    }

    /** Los feriados nacionales de ese ano, en orden. */
    static Set<LocalDate> delAno(int ano) {
        Set<LocalDate> dias = new TreeSet<>();

        //  Ano Nuevo, y el lunes 2 cuando el 1 cae domingo (Ley 20.983).
        LocalDate anoNuevo = LocalDate.of(ano, 1, 1);
        dias.add(anoNuevo);
        if (anoNuevo.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dias.add(anoNuevo.plusDays(1));
        }

        //  Viernes y Sabado Santo.
        LocalDate pascua = pascua(ano);
        dias.add(pascua.minusDays(2));
        dias.add(pascua.minusDays(1));

        dias.add(LocalDate.of(ano, 5, 1));    // Dia del Trabajo
        dias.add(LocalDate.of(ano, 5, 21));   // Glorias Navales

        //  Pueblos Indigenas: el dia del solsticio de invierno (Ley 21.357). En
        //  2021, su primer ano, la misma ley lo fijo el 21 de junio.
        dias.add(ano == 2021 ? LocalDate.of(2021, 6, 21) : solsticioDeInvierno(ano).atZone(CHILE).toLocalDate());

        dias.add(alLunes(LocalDate.of(ano, 6, 29)));   // San Pedro y San Pablo
        dias.add(LocalDate.of(ano, 7, 16));            // Virgen del Carmen
        dias.add(LocalDate.of(ano, 8, 15));            // Asuncion de la Virgen

        //  Fiestas Patrias, y el dia que las une con el fin de semana: el lunes
        //  17 y el viernes 20 (Ley 20.215), y el viernes 17 (Ley 20.983).
        LocalDate dieciocho = LocalDate.of(ano, 9, 18);
        dias.add(dieciocho);
        dias.add(dieciocho.plusDays(1));
        switch (dieciocho.getDayOfWeek()) {
            case TUESDAY, SATURDAY -> dias.add(dieciocho.minusDays(1));
            case WEDNESDAY -> dias.add(dieciocho.plusDays(2));
            default -> { }
        }

        dias.add(alLunes(LocalDate.of(ano, 10, 12)));  // Encuentro de Dos Mundos

        //  Iglesias Evangelicas y Protestantes (Ley 20.299): un martes pasa al
        //  viernes anterior, y un miercoles al viernes siguiente.
        LocalDate reforma = LocalDate.of(ano, 10, 31);
        dias.add(switch (reforma.getDayOfWeek()) {
            case TUESDAY -> reforma.minusDays(4);
            case WEDNESDAY -> reforma.plusDays(2);
            default -> reforma;
        });

        dias.add(LocalDate.of(ano, 11, 1));   // Todos los Santos
        dias.add(LocalDate.of(ano, 12, 8));   // Inmaculada Concepcion
        dias.add(LocalDate.of(ano, 12, 25));  // Navidad
        return Set.copyOf(dias);
    }

    /** Ley 19.668: de martes a jueves pasa al lunes de esa semana, y un viernes al lunes siguiente. */
    static LocalDate alLunes(LocalDate dia) {
        return switch (dia.getDayOfWeek()) {
            case TUESDAY, WEDNESDAY, THURSDAY -> dia.with(DayOfWeek.MONDAY);
            case FRIDAY -> dia.plusDays(3);
            default -> dia;
        };
    }

    /** El domingo de Pascua, con el algoritmo gregoriano anonimo (Meeus, cap. 8). */
    static LocalDate pascua(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mesYDia = h + l - 7 * m + 114;
        return LocalDate.of(ano, mesYDia / 31, mesYDia % 31 + 1);
    }

    /**
     * El instante del solsticio de junio (Meeus, Astronomical Algorithms, cap.
     * 27): un valor medio corregido por 24 terminos periodicos, con un error de
     * un minuto o menos entre los anos 1000 y 3000.
     */
    static Instant solsticioDeInvierno(int ano) {
        double y = (ano - 2000) / 1000.0;
        double medio = 2451716.56767 + 365241.62603 * y + 0.00325 * y * y + 0.00888 * y * y * y
                - 0.00030 * y * y * y * y;
        double t = (medio - 2451545.0) / 36525;
        double w = Math.toRadians(35999.373 * t - 2.47);
        double lambda = 1 + 0.0334 * Math.cos(w) + 0.0007 * Math.cos(2 * w);
        double suma = 0;
        for (double[] termino : TERMINOS) {
            suma += termino[0] * Math.cos(Math.toRadians(termino[1] + termino[2] * t));
        }
        double diaJuliano = medio + 0.00001 * suma / lambda;
        //  El dia juliano 2440587.5 es el 1 de enero de 1970 a medianoche (UTC).
        double segundos = (diaJuliano - 2440587.5) * 86400 - DELTA_T_SEGUNDOS;
        return Instant.ofEpochMilli(Math.round(segundos * 1000));
    }
}
