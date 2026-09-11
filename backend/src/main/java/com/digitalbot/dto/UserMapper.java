package com.digitalbot.dto;

import com.digitalbot.catalog.PlanCatalog;
import com.digitalbot.domain.UserAccount;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UserMapper {

    private UserMapper() {}

    public static Map<String, Object> publicUser(UserAccount user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("planId", user.getPlanId());
        map.put("createdAt", user.getCreatedAt());
        map.put("plan", PlanCatalog.find(user.getPlanId()));
        return map;
    }
}
