package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;
import com.enterprise.chat.engine.api.ChatDtos.MessageResponse;
import com.enterprise.chat.engine.model.ReceiptState;
import com.enterprise.chat.engine.model.*;
import com.enterprise.chat.engine.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MessageApplicationService {
    private final ChatMessageRepository messages;
    private final RoomRepository rooms;
    private final RoomMembershipRepository memberships;
    private final UserRepository users;
    private final MessageReceiptRepository receipts;
    private final ChatOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public MessageApplicationService(ChatMessageRepository messages, RoomRepository rooms,
                                    RoomMembershipRepository memberships, UserRepository users,
                                    MessageReceiptRepository receipts, ChatOutboxRepository outbox,
                                    ObjectMapper objectMapper) {
        this.messages = messages;
        this.rooms = rooms;
        this.memberships = memberships;
        this.users = users;
        this.receipts = receipts;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatEvent send(long senderId, String roomId, String clientMessageId, String content) {
        if (roomId == null || roomId.isBlank() || roomId.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid roomId is required.");
        }
        UUID clientId;
        try {
            clientId = UUID.fromString(clientMessageId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientMessageId must be a UUID.");
        }
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty() || normalized.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1-4000 characters.");
        }
        ChatMessageEntity existing = messages.findBySenderIdAndClientMessageId(senderId, clientId.toString()).orElse(null);
        if (existing != null) return ChatEvent.ack(clientId.toString(), existing.getId());

        if (!memberships.existsByRoomIdAndUserId(roomId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room before sending messages.");
        }
        RoomEntity room = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found."));
        UserEntity sender = users.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ChatMessageEntity message = messages.save(new ChatMessageEntity(room, sender, clientId.toString(), normalized));
        memberships.findAllByRoomId(roomId).stream()
                .map(RoomMembershipEntity::getUser)
                .filter(recipient -> !recipient.getId().equals(senderId))
                .forEach(recipient -> receipts.save(new MessageReceiptEntity(message, recipient)));

        ChatEvent event = ChatEvent.from(message);
        try {
            outbox.save(new ChatOutboxEntity(objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize persisted chat event.", ex);
        }
        return ChatEvent.ack(clientId.toString(), message.getId());
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> history(long userId, String roomId, Instant before, String beforeId, int limit) {
        if (!memberships.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room before reading history.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        var page = org.springframework.data.domain.PageRequest.of(0, safeLimit);
        List<ChatMessageEntity> found = before == null
                ? messages.findByRoomIdOrderByCreatedAtDescIdDesc(roomId, page)
                : beforeId == null
                ? messages.findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(roomId, before, page)
                : messages.findHistoryBefore(roomId, before, beforeId, page);
        List<String> authoredMessageIds = found.stream()
                .filter(message -> message.getSender().getId().equals(userId))
                .map(ChatMessageEntity::getId).toList();
        java.util.Map<String, String> receiptStates = authoredMessageIds.isEmpty()
                ? java.util.Map.of()
                : receipts.findReceiptStates(authoredMessageIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MessageReceiptRepository.ReceiptSummary::getMessageId,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.mapping(
                                        MessageReceiptRepository.ReceiptSummary::getState,
                                        java.util.stream.Collectors.maxBy(
                                                java.util.Comparator.comparingInt(state -> state.ordinal()))),
                                result -> result.map(ReceiptState::name).orElse(ReceiptState.SENT.name()))));
        return found.stream().map(message -> {
            String receiptState = message.getSender().getId().equals(userId)
                    ? receiptStates.getOrDefault(message.getId(), ReceiptState.SENT.name())
                    : null;
            return new MessageResponse(message.getId(), message.getRoom().getId(),
                    message.getSender().getId(), message.getSender().getDisplayName(),
                    message.getClientMessageId(), message.getContent(), message.getCreatedAt(),
                    receiptState, message.getEditedAt());
        }).toList();
    }

    @Transactional
    public ChatEvent edit(long userId, String messageId, String content) {
        ChatMessageEntity message = ownedMessage(userId, messageId);
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty() || normalized.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1-4000 characters.");
        }
        message.updateContent(normalized);
        ChatEvent event = ChatEvent.messageUpdated(message, false);
        saveEvent(event);
        return event;
    }

    @Transactional
    public ChatEvent delete(long userId, String messageId) {
        ChatMessageEntity message = ownedMessage(userId, messageId);
        ChatEvent event = ChatEvent.messageUpdated(message, true);
        saveEvent(event);
        receipts.deleteAllByMessageId(messageId);
        messages.delete(message);
        return event;
    }

    @Transactional
    public ChatEvent markReceipt(long userId, String messageId, ReceiptState state) {
        ChatMessageEntity message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
        if (!memberships.existsByRoomIdAndUserId(message.getRoom().getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a room member.");
        }
        if (message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authors do not create delivery receipts for their own messages.");
        }
        MessageReceiptEntity receipt = receipts.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> new MessageReceiptEntity(message,
                        users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))));
        if (receipt.getState().ordinal() < state.ordinal()) receipt.setState(state);
        receipts.save(receipt);
        return ChatEvent.receipt(message.getRoom().getId(), message.getId(),
                message.getSender().getId(), userId, state.name());
    }

    private ChatMessageEntity ownedMessage(long userId, String messageId) {
        ChatMessageEntity message = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
        if (!memberships.existsByRoomIdAndUserId(message.getRoom().getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a room member.");
        }
        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only modify your own messages.");
        }
        return message;
    }

    private void saveEvent(ChatEvent event) {
        try {
            outbox.save(new ChatOutboxEntity(objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize chat event.", ex);
        }
    }
}
