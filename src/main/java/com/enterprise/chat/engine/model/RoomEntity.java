package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms", indexes = @Index(name = "idx_chat_rooms_name", columnList = "name", unique = true))
public class RoomEntity {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "invite_code", nullable = false, length = 36)
    private String inviteCode = UUID.randomUUID().toString();

    protected RoomEntity() { }

    public RoomEntity(String name, UserEntity createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UserEntity getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isPublic() { return isPublic; }
    public String getInviteCode() { return inviteCode; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public void rotateInviteCode() { this.inviteCode = UUID.randomUUID().toString(); }
}
