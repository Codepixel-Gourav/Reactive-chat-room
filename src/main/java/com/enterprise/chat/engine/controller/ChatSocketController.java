package com.enterprise.chat.engine.controller;

import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;
import com.enterprise.chat.engine.api.ChatDtos.MessageResponse;
import com.enterprise.chat.engine.api.SocketDtos.ClientEvent;
import com.enterprise.chat.engine.model.ReceiptState;
import com.enterprise.chat.engine.service.MessageApplicationService;
import com.enterprise.chat.engine.service.PresenceService;
import com.enterprise.chat.engine.service.RoomEventPublisher;
import com.enterprise.chat.engine.service.RoomApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Controller
public class ChatSocketController {
    private final MessageApplicationService messages;
    private final RoomApplicationService rooms;
    private final SimpMessagingTemplate messaging;
    private final PresenceService presence;
    private final RoomEventPublisher eventPublisher;

    public ChatSocketController(MessageApplicationService messages, RoomApplicationService rooms,
                                SimpMessagingTemplate messaging, PresenceService presence,
                                RoomEventPublisher eventPublisher) {
        this.messages = messages;
        this.rooms = rooms;
        this.messaging = messaging;
        this.presence = presence;
        this.eventPublisher = eventPublisher;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid ClientEvent event, Principal principal) {
        String clientMessageId = event.clientMessageId();
        try {
            ChatEvent ack = messages.send(userId(principal), event.roomId(),
                    clientMessageId, event.content());
            messaging.convertAndSendToUser(principal.getName(), "/queue/events", ack);
        } catch (ResponseStatusException ex) {
            messaging.convertAndSendToUser(principal.getName(), "/queue/events",
                    ChatEvent.error(clientMessageId, ex.getReason()));
        }
    }

    @MessageMapping("/chat.typing")
    public void typing(ClientEvent event, Principal principal) {
        requireRoomId(event.roomId());
        long userId = userId(principal);
        rooms.requireMember(event.roomId(), userId);
        eventPublisher.publish(ChatEvent.typing(event.roomId(), userId));
    }

    @MessageMapping("/room.join")
    public void roomJoined(ClientEvent event, Principal principal, SimpMessageHeaderAccessor headers) {
        requireRoomId(event.roomId());
        rooms.requireMember(event.roomId(), userId(principal));
        if (headers.getSessionId() != null) {
            presence.activateRoom(userId(principal), headers.getSessionId(), event.roomId());
        }
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal, SimpMessageHeaderAccessor headers) {
        if (headers.getSessionId() != null) {
            presence.heartbeat(userId(principal), headers.getSessionId());
        }
    }

    @MessageMapping("/chat.receipt")
    public void receipt(ClientEvent event, Principal principal) {
        if (event.messageId() == null || event.messageId().isBlank() || event.messageId().length() > 36) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid messageId is required.");
        }
        ReceiptState state;
        if (event.state() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt state must be DELIVERED or READ.");
        }
        try {
            state = ReceiptState.valueOf(event.state());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt state must be DELIVERED or READ.");
        }
        if (state == ReceiptState.SENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid receipt transition.");
        }
        eventPublisher.publish(messages.markReceipt(userId(principal), event.messageId(), state));
    }

    @MessageMapping("/chat.edit")
    public void edit(ClientEvent event, Principal principal) {
        handleMessageAction(principal, event.messageId(),
                () -> messages.edit(userId(principal), event.messageId(), event.content()));
    }

    @MessageMapping("/chat.delete")
    public void delete(ClientEvent event, Principal principal) {
        handleMessageAction(principal, event.messageId(),
                () -> messages.delete(userId(principal), event.messageId()));
    }

    @GetMapping("/api/rooms/{roomId}/messages")
    @ResponseBody
    public List<MessageResponse> history(Authentication authentication, @PathVariable String roomId,
                                         @RequestParam(required = false) Instant before,
                                         @RequestParam(required = false) String beforeId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return messages.history(userId(authentication), roomId, before, beforeId, limit);
    }

    private static long userId(Principal principal) {
        return Long.parseLong(principal.getName());
    }

    private static void requireRoomId(String roomId) {
        if (roomId == null || roomId.isBlank() || roomId.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid roomId is required.");
        }
    }

    private void handleMessageAction(Principal principal, String messageId,
                                     java.util.function.Supplier<ChatEvent> action) {
        try {
            if (messageId == null || messageId.isBlank() || messageId.length() > 36) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid messageId is required.");
            }
            action.get();
        } catch (ResponseStatusException ex) {
            messaging.convertAndSendToUser(principal.getName(), "/queue/events",
                    ChatEvent.error(messageId, ex.getReason()));
        }
    }
}
