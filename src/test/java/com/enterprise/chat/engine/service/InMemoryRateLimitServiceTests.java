package com.enterprise.chat.engine.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimitServiceTests {
    @Test
    void enforcesLimitsPerSubjectAndEventType() {
        InMemoryRateLimitService rateLimiter = new InMemoryRateLimitService(30, 60, 300, 30);

        assertTrue(rateLimiter.allow("user:1", "message", 1));
        assertFalse(rateLimiter.allow("user:1", "message", 1));
        assertTrue(rateLimiter.allow("user:2", "message", 1));
        assertTrue(rateLimiter.allow("user:1", "typing", 1));
    }
}
