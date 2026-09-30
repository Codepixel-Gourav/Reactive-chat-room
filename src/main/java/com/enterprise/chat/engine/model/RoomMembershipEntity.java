package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_room_user", columnNames = {"room_id", "user_id"}),
        indexes = @Index(name = "idx_membership_user_room", columnList = "user_id, room_id"))
public class RoomMembershipEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected RoomMembershipEntity() { }

    public RoomMembershipEntity(RoomEntity room, UserEntity user, String role) {
        this.room = room;
        this.user = user;
        this.role = role;
    }

    public Long getId() { return id; }
    public RoomEntity getRoom() { return room; }
    public UserEntity getUser() { return user; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
}
