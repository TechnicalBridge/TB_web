package com.tbridge.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** El mensaje nuevo y la conversacion hasta ahora. */
public record Pregunta(
        @Schema(description = "Lo que escribio el deudor", example = "¿Cuánto debo?")
        String message,
        @Schema(description = "La conversacion anterior: [{role: user|assistant, content}]")
        List<Map<String, Object>> messages) {
}
