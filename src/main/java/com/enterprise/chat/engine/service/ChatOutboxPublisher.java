package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.repository.ChatOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatOutboxPublisher.class);
    private final ChatOutboxRepository outbox;
    private final RoomEventPublisher publisher;

    public ChatOutboxPublisher(ChatOutboxRepository outbox, RoomEventPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${chat.outbox.poll-interval-ms:250}",
            initialDelayString = "${chat.outbox.initial-delay-ms:3000}")
    @Transactional
    public void publishPending() {
        try {
            outbox.lockPending().forEach(row -> {
                publisher.publish(row.getPayload());
                row.markPublished();
            });
        } catch (RuntimeException ex) {
            LOGGER.error("Chat outbox publishing failed; records remain pending for retry.", ex);
            throw ex;
        }
    }
}
