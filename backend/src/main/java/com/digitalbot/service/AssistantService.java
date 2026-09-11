package com.digitalbot.service;

import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.dto.QuoteResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final QuoteService quoteService;
    private final String apiKey;
    private final String model;
    private final RestClient xaiClient;

    public AssistantService(
            QuoteService quoteService,
            @Value("${xai.api-key:}") String apiKey,
            @Value("${xai.base-url:https://api.x.ai/v1}") String baseUrl,
            @Value("${xai.model:grok-4.5}") String model
    ) {
        this.quoteService = quoteService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.xaiClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> chat(List<Map<String, Object>> messages, QuoteRequest quoteRequest) {
        List<Map<String, String>> last = new ArrayList<>();
        if (messages != null) {
            for (Map<String, Object> message : messages) {
                if (message == null || message.get("content") == null) {
                    continue;
                }
                String role = String.valueOf(message.getOrDefault("role", "user"));
                last.add(Map.of(
                        "role", "assistant".equals(role) ? "assistant" : "user",
                        "content", String.valueOf(message.get("content"))
                ));
            }
            if (last.size() > 12) {
                last = last.subList(last.size() - 12, last.size());
            }
        }
        if (last.isEmpty()) {
            throw new com.digitalbot.web.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Escribe un mensaje"
            );
        }

        QuoteResult hint = null;
        try {
            if (quoteRequest != null && quoteRequest.planId() != null) {
                hint = quoteService.quote(quoteRequest);
            }
        } catch (Exception ignored) {
            hint = null;
        }

        String reply;
        String provider = "local-verified";
        if (!apiKey.isEmpty()) {
            try {
                reply = grokReply(last, hint);
                provider = "spacexai";
            } catch (Exception e) {
                log.warn("SpaceXAI no disponible, usando respuestas locales: {}", e.getMessage());
                reply = localReply(last.get(last.size() - 1).get("content"), hint);
            }
        } else {
            reply = localReply(last.get(last.size() - 1).get("content"), hint);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reply", reply);
        body.put("verified", true);
        body.put("provider", provider);
        return body;
    }

    private String grokReply(List<Map<String, String>> messages, QuoteResult hint) {
        String system = """
                Eres el Sistema Automatizado Verificado de DIGITAL BOT, una plataforma de bots, planes y pagos simulados.
                Planes: Esencial 19 USD/mes (182 anual), Profesional 49 USD/mes (470 anual, recomendado), Corporativo 129 USD/mes (1238 anual).
                Extras: facturación 12, reportes 9, SLA 20 (USD/mes). Usuarios extra: 8 USD/mes.
                Hablas en español, tono profesional y breve (máximo 120 palabras).
                NUNCA inventes un token. El token lo emite el backend al pulsar "Verificar y emitir token".
                Explica que, con el token válido, aparece la forma de pago (tarjeta, transferencia o billetera). Los cobros son una simulación.
                """;
        if (hint != null) {
            system += "Cotización activa: plan " + hint.plan().name() + ", " + hint.amount() + " USD / " + hint.periodLabel() + ".";
        }

        List<Map<String, String>> payload = new ArrayList<>();
        payload.add(Map.of("role", "system", "content", system));
        payload.addAll(messages);

        ChatResponse response = xaiClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "messages", payload))
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            return localReply(messages.get(messages.size() - 1).get("content"), hint);
        }
        return response.choices().get(0).message().content();
    }

    private String localReply(String message, QuoteResult hint) {
        String text = message == null ? "" : message.toLowerCase();
        if (Pattern.compile("corporativo|empresa|escala|sla|api").matcher(text).find()) {
            return "Para operación a escala el plan Corporativo es el encaje verificado: multi-equipo, SLA y auditoría. Cuando quieras pagar, pulsa «Verificar y emitir token»; el sistema automatizado firmará el token y entonces aparecerá la forma de pago.";
        }
        if (Pattern.compile("esencial|barato|empezar|básico|basico").matcher(text).find()) {
            return "El plan Esencial cubre lista de pagos y simulador con hasta 3 bots. Si más adelante necesitas la IA verificada y el token de cobro, conviene subir a Profesional. Pulsa «Verificar y emitir token» para desbloquear la forma de pago.";
        }
        if (Pattern.compile("profesional|pro|equipo|ia|token").matcher(text).find()) {
            return "El plan Profesional es el más elegido: IA verificada, bots ilimitados y emisión de token de pago. Confirma el plan y pulsa «Verificar y emitir token». Sin ese token la forma de pago permanece oculta.";
        }
        if (Pattern.compile("precio|cuesta|cuánto|cuanto|coste|costo").matcher(text).find()) {
            String extra = hint != null
                    ? " La cotización actual es " + hint.amount() + " " + hint.currency() + " / " + hint.periodLabel() + " (" + hint.plan().name() + ")."
                    : " Esencial 19 USD/mes, Profesional 49 USD/mes, Corporativo 129 USD/mes. El anual aplica 20% de ahorro.";
            return "Precios públicos DIGITAL BOT." + extra + " El cobro solo se habilita con un token emitido por el sistema automatizado verificado.";
        }
        if (Pattern.compile("pago|pagar|tarjeta|transferencia").matcher(text).find()) {
            return "La forma de pago no se muestra hasta que el sistema automatizado verificado emite un token (formato DBT-XXXX-XXXX-XXXX). Elígelo en Simular plan o aquí mismo y pulsa «Verificar y emitir token».";
        }
        return "Soy el sistema automatizado verificado de DIGITAL BOT. Puedo recomendarte Esencial, Profesional o Corporativo según el tamaño del equipo y si necesitas token de cobro. Dime cuántos usuarios tienes y si facturas en mensual o anual, y luego emitimos el token para revelar la forma de pago.";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Choice(Message message) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Message(String content) {}
    }
}
