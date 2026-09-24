package com.tbridge.common.exception;

/**
 * La forma de todo error de los servicios: {@code {"error": "texto"}}.
 *
 * <p>El texto esta escrito para una persona: el portal lo muestra tal cual.
 */
public record ApiError(String error) {
}
