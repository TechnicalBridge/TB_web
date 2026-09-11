package com.digitalbot.web;

import com.digitalbot.catalog.PlanCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PlanController {

    @GetMapping("/api/plans")
    public Map<String, Object> plans() {
        return Map.of("plans", PlanCatalog.PLANS, "extras", PlanCatalog.extras());
    }
}
