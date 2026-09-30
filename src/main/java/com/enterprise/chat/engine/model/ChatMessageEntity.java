package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_message_sender_client_id", columnNames = {"sender_id", "client_message_id"}),
        indexes = {
                @Index(name = "idx_message_room_time_id", columnList = "room_id, created_at, id"),
                @Index(name = "idx_message_sender_time", columnList = "sender_id, created_at")
        })
public class ChatMessageEntity {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Column(name = "client_message_id", nullable = false, length = 36)
    private String clientMessageId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    protected ChatMessageEntity() { }

    public ChatMessageEntity(RoomEntity room, UserEntity sender, String clientMessageId, String content) {
        this.room = room;
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }

    public String getId() { return id; }
    public RoomEntity getRoom() { return room; }
    public UserEntity getSender() { return sender; }
    public String getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public void updateContent(String content) {
        this.content = content;
        this.editedAt = Instant.now();
    }
}
