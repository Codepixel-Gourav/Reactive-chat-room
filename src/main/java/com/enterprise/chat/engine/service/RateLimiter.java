package com.enterprise.chat.engine.service;

public interface RateLimiter {
    boolean allow(long userId, String eventType);
    boolean allow(String subject, String eventType, int limit);
}
