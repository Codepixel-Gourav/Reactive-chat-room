package com.enterprise.chat.engine.api;

import com.enterprise.chat.engine.model.ChatMessageEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ChatDtos {
    private ChatDtos() { }
    public record CreateRoomRequest(@NotBlank @Size(min = 2, max = 80) String name, Boolean isPublic) { }
    public record RoomSettingsRequest(boolean isPublic) { }
    public record RoomResponse(String id, String name, int memberCount, long creatorId,
                               boolean isPublic, String inviteCode, String role) { }
    public record SendMessageRequest(@NotBlank @Size(max = 36) String clientMessageId,
                                     @NotBlank @Size(max = 80) String roomId,
                                     @NotBlank @Size(max = 4000) String content) { }
    public record ChatEvent(String type, String id, String roomId, String clientMessageId,
                            Long senderId, String sender, String content, Instant timestamp,
                            String status, String detail, java.util.List<MemberStatus> members,
                            String inviteCode, boolean isPublic) {
        public static ChatEvent from(ChatMessageEntity message) {
            return new ChatEvent("CHAT", message.getId(), message.getRoom().getId(),
                    message.getClientMessageId(), message.getSender().getId(),
                    message.getSender().getDisplayName(), message.getContent(),
                    message.getCreatedAt(), null, null, null, null, false);
        }
        public static ChatEvent ack(String clientMessageId, String messageId) {
            return new ChatEvent("ACK", messageId, null, clientMessageId,
                    null, null, null, Instant.now(), "accepted", null, null, null, false);
        }
        public static ChatEvent error(String clientMessageId, String detail) {
            return new ChatEvent("ERROR", null, null, clientMessageId,
                    null, null, null, Instant.now(), null, detail, null, null, false);
        }
        public static ChatEvent typing(String roomId, long senderId) {
            return new ChatEvent("TYPING", null, roomId, null, senderId,
                    null, null, Instant.now(), null, null, null, null, false);
        }
        public static ChatEvent members(String roomId, java.util.List<MemberStatus> members) {
            return new ChatEvent("MEMBERS", null, roomId, null, null,
                    null, null, Instant.now(), null, null, members, null, false);
        }
        public static ChatEvent receipt(String roomId, String messageId, long authorId,
                                        long readerId, String state) {
            return new ChatEvent("RECEIPT", messageId, roomId, null, authorId,
                    null, null, Instant.now(), state, Long.toString(readerId), null, null, false);
        }
        public static ChatEvent roomRemoved(String roomId, boolean deleted) {
            return new ChatEvent(deleted ? "ROOM_DELETED" : "ROOM_REMOVED", roomId, roomId,
                    null, null, null, null, Instant.now(), null, null, null, null, false);
        }
        public static ChatEvent messageUpdated(ChatMessageEntity message, boolean deleted) {
            return new ChatEvent(deleted ? "MESSAGE_DELETED" : "MESSAGE_EDITED", message.getId(),
                    message.getRoom().getId(), message.getClientMessageId(), message.getSender().getId(),
                    message.getSender().getDisplayName(), deleted ? null : message.getContent(),
                    Instant.now(), null, null, null, null, false);
        }
    }
    public record MemberStatus(long id, String name, boolean online) { }
    public record MessageResponse(String id, String roomId, long senderId, String sender,
                                  String clientMessageId, String content, Instant timestamp,
                                  String receiptState, Instant editedAt) {
        public static MessageResponse from(ChatMessageEntity message) {
            return new MessageResponse(message.getId(), message.getRoom().getId(),
                    message.getSender().getId(), message.getSender().getDisplayName(),
                    message.getClientMessageId(), message.getContent(), message.getCreatedAt(),
                    null, message.getEditedAt());
        }
    }
}
