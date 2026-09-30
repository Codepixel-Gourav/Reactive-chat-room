package com.enterprise.chat.engine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room_bans",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_ban_room_user", columnNames = {"room_id", "user_id"}))
public class RoomBanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banned_by", nullable = false)
    private UserEntity bannedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RoomBanEntity() { }

    public RoomBanEntity(RoomEntity room, UserEntity user, UserEntity bannedBy) {
        this.room = room;
        this.user = user;
        this.bannedBy = bannedBy;
    }
}
