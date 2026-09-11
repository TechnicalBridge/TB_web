package com.digitalbot.dto;

import com.digitalbot.catalog.PlanCatalog;
import com.digitalbot.domain.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    @Test
    void publicUser_mapsUserAndPlan() {
        UserAccount user = new UserAccount();
        user.setId("u-42");
        user.setName("María");
        user.setEmail("maria@example.com");
        user.setRole("user");
        user.setPlanId("esencial");
        user.setCreatedAt(Instant.parse("2024-01-15T08:30:00Z"));

        Map<String, Object> result = UserMapper.publicUser(user);

        assertEquals("u-42", result.get("id"));
        assertEquals("María", result.get("name"));
        assertEquals("maria@example.com", result.get("email"));
        assertEquals("user", result.get("role"));
        assertEquals("esencial", result.get("planId"));
        assertEquals(user.getCreatedAt(), result.get("createdAt"));
        assertNotNull(result.get("plan"));
        PlanCatalog.Plan plan = (PlanCatalog.Plan) result.get("plan");
        assertEquals("esencial", plan.id());
        assertEquals("Esencial", plan.name());
    }
}
