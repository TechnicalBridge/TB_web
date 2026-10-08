package com.tbridge.debt.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El RUT: normalizarlo y verificar su digito verificador.
 *
 * <p>Cada servicio que lo usa tiene su copia (ms-auth y ms-debt), para que
 * ninguno dependa de una libreria compartida. Son pocas lineas y la regla del
 * modulo 11 no cambia; si alguna vez cambia, hay que cambiar las dos.
 *
 * <p>Dos cosas distintas que conviene no confundir: {@link #normalizar} deja
 * el RUT en su forma canonica para que el UNIQUE funcione, y {@link #esValido}
 * revisa el modulo 11. Un RUT puede tener formato perfecto y no existir.
 */
public final class Rut {

    private static final Pattern CANONICO = Pattern.compile("^\\d{7,8}-[\\dK]$");

    private Rut() {
    }

    /** '76.418.902-7', '764189027' y '76418902-7' quedan todos iguales. */
    public static String normalizar(String crudo) {
        String limpio = (crudo == null ? "" : crudo).trim().toUpperCase(Locale.ROOT)
                .replace(".", "").replace(" ", "");
        if (!limpio.contains("-") && limpio.length() > 1) {
            limpio = limpio.substring(0, limpio.length() - 1) + "-"
                    + limpio.charAt(limpio.length() - 1);
        }
        return limpio;
    }

    public static boolean tieneFormato(String rut) {
        return rut != null && CANONICO.matcher(rut).matches();
    }

    /** Modulo 11: se pondera de derecha a izquierda con 2,3,4,5,6,7 y se repite. */
    public static String digitoVerificador(String cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - suma % 11;
        return resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
    }

    public static boolean esValido(String crudo) {
        String normalizado = normalizar(crudo);
        if (!tieneFormato(normalizado)) {
            return false;
        }
        String[] partes = normalizado.split("-");
        return digitoVerificador(partes[0]).equals(partes[1]);
    }
}
