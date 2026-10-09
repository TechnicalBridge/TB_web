package com.tbridge.ai.util;

import java.util.Collection;
import java.util.Map;

/**
 * Los mensajes de la conversacion llegan como JSON libre ({@code role},
 * {@code content} o {@code message}). Esto dice si un campo trae algo.
 */
public final class Valor {

    private Valor() {
    }

    /** Vacio es nulo, texto vacio, cero, falso o una lista sin nada. */
    public static boolean presente(Object valor) {
        return switch (valor) {
            case null -> false;
            case String texto -> !texto.isEmpty();
            case Boolean si -> si;
            case Number numero -> numero.doubleValue() != 0;
            case Collection<?> lista -> !lista.isEmpty();
            case Map<?, ?> mapa -> !mapa.isEmpty();
            default -> true;
        };
    }

    /** El primero que trae algo, o null. */
    public static Object primero(Object... valores) {
        for (Object valor : valores) {
            if (presente(valor)) {
                return valor;
            }
        }
        return null;
    }

    /** Como texto; nulo es texto vacio. */
    public static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }
}
