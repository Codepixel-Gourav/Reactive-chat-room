package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;

public record RoomPresenceChangedEvent(ChatEvent event, Long recipientUserId) {
    public RoomPresenceChangedEvent(ChatEvent event) {
        this(event, null);
    }
}
