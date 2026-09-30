package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_receipt_message_user", columnNames = {"message_id", "user_id"}),
        indexes = @Index(name = "idx_receipt_user_state", columnList = "user_id, state"))
public class MessageReceiptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessageEntity message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReceiptState state = ReceiptState.SENT;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MessageReceiptEntity() { }

    public MessageReceiptEntity(ChatMessageEntity message, UserEntity user) {
        this.message = message;
        this.user = user;
    }

    public ReceiptState getState() { return state; }

    public void setState(ReceiptState state) {
        if (this.state.ordinal() < state.ordinal()) {
            this.state = state;
            this.updatedAt = Instant.now();
        }
    }
}
