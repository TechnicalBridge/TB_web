package com.tbridge.debt.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Responde {@link CarteraInvalida} con la forma del contrato.
 *
 * <p>Antes cada controlador que recibe cartera tenia su propia copia de este
 * manejador. Va primero ({@code HIGHEST_PRECEDENCE}): si no, el manejador
 * general de common, que atrapa toda excepcion, se la quedaria como un 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CarteraInvalidaHandler {

    @ExceptionHandler(CarteraInvalida.class)
    public ResponseEntity<ErrorContrato> alFallar(CarteraInvalida fallo) {
        return ResponseEntity.status(fallo.getStatus()).body(ErrorContrato.de(fallo));
    }
}
