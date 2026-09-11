package com.digitalbot.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserAccountTest {

    @Test
    void isGuest_returnsTrueForGuestRole() {
        UserAccount user = new UserAccount();
        user.setRole("guest");

        assertTrue(user.isGuest());
    }

    @Test
    void isGuest_returnsFalseForRegularUser() {
        UserAccount user = new UserAccount();
        user.setRole("user");

        assertFalse(user.isGuest());
    }

    @Test
    void beanFields_areStoredAndReadBack() {
        Instant createdAt = Instant.parse("2025-02-01T10:15:30Z");
        UserAccount user = new UserAccount();
        user.setId("u-1");
        user.setName("Ana");
        user.setEmail("ana@example.com");
        user.setPasswordHash("hash");
        user.setRole("admin");
        user.setPlanId("profesional");
        user.setCreatedAt(createdAt);

        assertEquals("u-1", user.getId());
        assertEquals("Ana", user.getName());
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        assertEquals("admin", user.getRole());
        assertEquals("profesional", user.getPlanId());
        assertEquals(createdAt, user.getCreatedAt());
    }
}
