package com.tbridge.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Huellas y comparaciones de secretos.
 *
 * <p>Ningun secreto se guarda en claro: ni el codigo de acceso, ni la llave de
 * renovacion, ni la clave de API. Se guarda su SHA-256, y un respaldo de las
 * bases no le sirve a nadie para entrar. Estaba escrito tres veces, una por
 * servicio.
 */
public final class Hash {

    private Hash() {
    }

    /** SHA-256 en hexadecimal: siempre 64 caracteres, como las columnas CHAR(64). */
    public static String sha256(String texto) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256")
                    .digest((texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(huella);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    /**
     * Compara sin delatar cuanto coincide.
     *
     * <p>Una comparacion normal se detiene en el primer caracter distinto, y
     * ese tiempo, medido muchas veces, revela el valor de a un caracter.
     * {@link MessageDigest#isEqual} recorre todo siempre.
     */
    public static boolean igualesEnTiempoConstante(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
