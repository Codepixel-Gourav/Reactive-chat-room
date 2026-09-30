package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomEventPublisher {
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;

    public RoomEventPublisher(SimpMessagingTemplate messaging, ObjectMapper objectMapper) {
        this.messaging = messaging;
        this.objectMapper = objectMapper;
    }

    public void publish(String serializedEvent) {
        try {
            publish(objectMapper.readValue(serializedEvent, ChatEvent.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize a room event.", ex);
        }
    }

    public void publish(ChatEvent event) {
        if (event.roomId() != null) {
            messaging.convertAndSend("/topic/rooms/" + event.roomId(), event);
        }
    }

    public void publishToUser(long userId, ChatEvent event) {
        messaging.convertAndSendToUser(Long.toString(userId), "/queue/events", event);
    }
}
