package com.digitalbot.web;

import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.dto.QuoteResult;
import com.digitalbot.service.QuoteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulateController {

    private final QuoteService quoteService;

    public SimulateController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/api/simulate")
    public QuoteResult simulate(@RequestBody(required = false) QuoteRequest request) {
        return quoteService.quote(request);
    }
}
