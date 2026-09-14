package com.tbridge.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class GatewayHealthController {

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
                "ok", true,
                "service", "tbridge-gateway",
                "architecture", "API Gateway · MS-Auth · MS-Debt · MS-Payments · MS-AI"
        ));
    }
}
