package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;
import com.enterprise.chat.engine.api.ChatDtos.MemberStatus;
import com.enterprise.chat.engine.repository.RoomMembershipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class PresenceService {
    private static final long SESSION_TTL_MILLIS = 90_000;
    private final ConcurrentMap<String, PresenceSession> sessions = new ConcurrentHashMap<>();
    private final RoomMembershipRepository memberships;
    private final ApplicationEventPublisher events;

    public PresenceService(RoomMembershipRepository memberships, ApplicationEventPublisher events) {
        this.memberships = memberships;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public void connect(long userId, String sessionId) {
        sessions.put(sessionId, new PresenceSession(userId, Instant.now().toEpochMilli() + SESSION_TTL_MILLIS));
    }

    public void heartbeat(long userId, String sessionId) {
        refresh(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public void disconnect(long userId, String sessionId) {
        PresenceSession session = sessions.remove(sessionId);
        if (session != null && session.userId == userId && session.activeRoom != null) {
            publishRoom(session.activeRoom);
        }
    }

    @Transactional(readOnly = true)
    public void activateRoom(long userId, String sessionId, String roomId) {
        PresenceSession session = sessions.get(sessionId);
        if (session == null || session.userId != userId) return;
        String previousRoom = session.activeRoom;
        session.activeRoom = roomId;
        if (previousRoom != null && !previousRoom.equals(roomId)) publishRoom(previousRoom);
        publishRoom(roomId);
    }

    public void removeUserFromRoom(long userId, String roomId) {
        sessions.values().forEach(session -> {
            if (session.userId == userId && roomId.equals(session.activeRoom)) session.activeRoom = null;
        });
        publishRoom(roomId);
    }

    public void removeRoom(String roomId) {
        sessions.values().forEach(session -> {
            if (roomId.equals(session.activeRoom)) session.activeRoom = null;
        });
    }

    public void publishRoomMembers(String roomId) {
        publishRoom(roomId);
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    @Transactional(readOnly = true)
    public void reapExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        Set<String> expiredRooms = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().expiresAt <= now)
                .map(Map.Entry::getValue)
                .map(session -> session.activeRoom)
                .filter(roomId -> roomId != null)
                .collect(Collectors.toSet());
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        expiredRooms.forEach(this::publishRoom);
    }

    private void refresh(long userId, String sessionId) {
        sessions.computeIfPresent(sessionId, (id, session) -> {
            if (session.userId == userId) session.expiresAt = Instant.now().toEpochMilli() + SESSION_TTL_MILLIS;
            return session;
        });
    }

    private void publishRoom(String roomId) {
        long now = Instant.now().toEpochMilli();
        Set<Long> activeUsers = sessions.values().stream()
                .filter(session -> session.expiresAt > now && roomId.equals(session.activeRoom))
                .map(session -> session.userId)
                .collect(Collectors.toSet());
        List<MemberStatus> members = memberships.findAllByRoomId(roomId).stream()
                .map(membership -> {
                    long userId = membership.getUser().getId();
                    boolean online = activeUsers.contains(userId);
                    return new MemberStatus(userId, membership.getUser().getDisplayName(), online);
                }).toList();
        events.publishEvent(new RoomPresenceChangedEvent(ChatEvent.members(roomId, members)));
    }

    private static final class PresenceSession {
        private final long userId;
        private volatile long expiresAt;
        private volatile String activeRoom;

        private PresenceSession(long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
