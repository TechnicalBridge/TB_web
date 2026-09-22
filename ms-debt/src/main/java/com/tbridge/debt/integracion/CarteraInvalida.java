package com.tbridge.debt.integracion;

/**
 * El lote completo no se puede procesar: nada de el entra.
 *
 * <p>Distinta de un rechazo por deuda. Una deuda mal formada se rechaza sola y
 * las demas siguen; esto es para lo que invalida el envio entero, como un
 * acreedor que no existe o un numero de lote reutilizado.
 */
public class CarteraInvalida extends RuntimeException {

    private final String codigo;
    private final int status;

    public CarteraInvalida(String codigo, String mensaje) {
        this(codigo, mensaje, 400);
    }

    public CarteraInvalida(String codigo, String mensaje, int status) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() {
        return codigo;
    }

    public int getStatus() {
        return status;
    }
}
