package com.enterprise.chat.engine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryRateLimitService implements RateLimiter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int messageLimit;
    private final int typingLimit;
    private final int receiptLimit;
    private final int roomJoinLimit;

    public InMemoryRateLimitService(
            @Value("${chat.rate-limit.messages-per-minute:30}") int messageLimit,
            @Value("${chat.rate-limit.typing-per-minute:60}") int typingLimit,
            @Value("${chat.rate-limit.receipts-per-minute:300}") int receiptLimit,
            @Value("${chat.rate-limit.room-joins-per-minute:30}") int roomJoinLimit) {
        this.messageLimit = messageLimit;
        this.typingLimit = typingLimit;
        this.receiptLimit = receiptLimit;
        this.roomJoinLimit = roomJoinLimit;
    }

    @Override
    public boolean allow(long userId, String eventType) {
        int limit = switch (eventType) {
            case "TYPING" -> typingLimit;
            case "RECEIPT" -> receiptLimit;
            case "ROOM_JOIN" -> roomJoinLimit;
            default -> messageLimit;
        };
        return allow("user:" + userId, eventType, limit);
    }

    @Override
    public boolean allow(String subject, String eventType, int limit) {
        long now = Instant.now().toEpochMilli();
        Window window = windows.compute(eventType + ":" + subject, (key, current) -> {
            if (current == null || current.expiresAt() <= now) {
                return new Window(now + WINDOW_MILLIS, 1);
            }
            return new Window(current.expiresAt(), current.count() + 1);
        });
        return window.count() <= limit;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void removeExpiredWindows() {
        long now = Instant.now().toEpochMilli();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Window(long expiresAt, int count) { }
}
