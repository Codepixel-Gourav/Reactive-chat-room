package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_outbox", indexes = @Index(name = "idx_outbox_pending", columnList = "published_at, id"))
public class ChatOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ChatOutboxEntity() { }
    public ChatOutboxEntity(String payload) { this.payload = payload; }
    public Long getId() { return id; }
    public String getPayload() { return payload; }
    public void markPublished() { this.publishedAt = Instant.now(); }
}
