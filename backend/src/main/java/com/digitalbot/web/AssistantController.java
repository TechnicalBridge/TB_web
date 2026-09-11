package com.digitalbot.web;

import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.service.AssistantService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/api/ai/chat")
    public Map<String, Object> chat(
            @AuthenticationPrincipal Object user,
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = body.get("messages") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        Integer users = body.get("users") instanceof Number n ? n.intValue() : null;
        @SuppressWarnings("unchecked")
        List<String> extras = body.get("extras") instanceof List<?> list
                ? (List<String>) list
                : List.of();
        QuoteRequest quote = new QuoteRequest(
                body.get("planId") == null ? null : String.valueOf(body.get("planId")),
                body.get("billing") == null ? null : String.valueOf(body.get("billing")),
                users,
                extras
        );
        return assistantService.chat(messages, quote);
    }
}
