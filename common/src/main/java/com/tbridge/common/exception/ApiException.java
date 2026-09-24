package com.tbridge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Un error que se le puede explicar a quien llamo: una regla de negocio que no
 * se cumple, algo que no existe, algo que no le corresponde ver.
 *
 * <p>Lleva su codigo HTTP para que {@link ApiExceptionHandler} lo responda tal
 * cual, con el mensaje como texto. Todo lo demas es un error interno.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
