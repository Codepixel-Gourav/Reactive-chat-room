package com.enterprise.chat.engine.service;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomPresenceEventRelay {
    private final SimpMessagingTemplate messaging;

    public RoomPresenceEventRelay(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(RoomPresenceChangedEvent event) {
        var chatEvent = event.event();
        if (event.recipientUserId() != null) {
            messaging.convertAndSendToUser(Long.toString(event.recipientUserId()), "/queue/events", chatEvent);
        } else {
            messaging.convertAndSend("/topic/rooms/" + chatEvent.roomId(), chatEvent);
        }
    }
}
