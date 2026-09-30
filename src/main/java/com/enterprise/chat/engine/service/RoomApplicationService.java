package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.ChatDtos.CreateRoomRequest;
import com.enterprise.chat.engine.api.ChatDtos.ChatEvent;
import com.enterprise.chat.engine.api.ChatDtos.RoomResponse;
import com.enterprise.chat.engine.api.ChatDtos.RoomSettingsRequest;
import com.enterprise.chat.engine.model.RoomBanEntity;
import com.enterprise.chat.engine.model.RoomEntity;
import com.enterprise.chat.engine.model.RoomMembershipEntity;
import com.enterprise.chat.engine.model.UserEntity;
import com.enterprise.chat.engine.repository.RoomMembershipRepository;
import com.enterprise.chat.engine.repository.RoomBanRepository;
import com.enterprise.chat.engine.repository.RoomRepository;
import com.enterprise.chat.engine.repository.UserRepository;
import com.enterprise.chat.engine.repository.ChatMessageRepository;
import com.enterprise.chat.engine.repository.MessageReceiptRepository;
import com.enterprise.chat.engine.repository.ChatOutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class RoomApplicationService {
    private final RoomRepository rooms;
    private final RoomMembershipRepository memberships;
    private final UserRepository users;
    private final RoomBanRepository bans;
    private final ChatMessageRepository messages;
    private final MessageReceiptRepository receipts;
    private final ChatOutboxRepository outbox;
    private final ApplicationEventPublisher events;
    private final PresenceService presence;

    public RoomApplicationService(RoomRepository rooms, RoomMembershipRepository memberships, UserRepository users,
                                  RoomBanRepository bans, ChatMessageRepository messages,
                                  MessageReceiptRepository receipts, ChatOutboxRepository outbox,
                                  ApplicationEventPublisher events, PresenceService presence) {
        this.rooms = rooms;
        this.memberships = memberships;
        this.users = users;
        this.bans = bans;
        this.messages = messages;
        this.receipts = receipts;
        this.outbox = outbox;
        this.events = events;
        this.presence = presence;
    }

    @Transactional
    public RoomResponse create(long userId, CreateRoomRequest request) {
        String name = request.name().trim();
        if (rooms.findByNameIgnoreCase(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That room already exists.");
        }
        UserEntity user = user(userId);
        RoomEntity room = rooms.save(new RoomEntity(name, user));
        room.setPublic(request.isPublic() == null || request.isPublic());
        memberships.save(new RoomMembershipEntity(room, user, "OWNER"));
        return toResponse(room, userId);
    }

    @Transactional
    public RoomResponse join(long userId, String roomId, String inviteCode) {
        RoomEntity room = room(roomId);
        if (bans.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are banned from this room.");
        }
        boolean isOwner = room.getCreatedBy().getId().equals(userId);
        if (!room.isPublic() && !room.getInviteCode().equals(inviteCode)
                && !memberships.existsByRoomIdAndUserId(roomId, userId) && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A valid private room invite is required.");
        }
        UserEntity user = user(userId);
        if (!memberships.existsByRoomIdAndUserId(roomId, userId)) {
            memberships.save(new RoomMembershipEntity(room, user, isOwner ? "OWNER" : "MEMBER"));
        }
        return toResponse(room, userId);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(long userId) {
        return memberships.findAllByUserId(userId).stream()
                .map(membership -> toResponse(membership.getRoom(), userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> publicRooms(long userId) {
        return rooms.findPublicRoomsForUser(userId).stream().map(room -> toResponse(room, userId)).toList();
    }

    @Transactional
    public RoomResponse updateSettings(long userId, String roomId, RoomSettingsRequest settings) {
        RoomEntity room = room(roomId);
        requireOwner(room, userId);
        room.setPublic(settings.isPublic());
        return toResponse(room, userId);
    }

    @Transactional
    public RoomResponse rotateInvite(long userId, String roomId) {
        RoomEntity room = room(roomId);
        requireOwner(room, userId);
        room.rotateInviteCode();
        return toResponse(room, userId);
    }

    @Transactional
    public void leave(long userId, String roomId) {
        RoomEntity room = room(roomId);
        if (!memberships.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of this room.");
        }
        memberships.deleteByRoomIdAndUserId(roomId, userId);
        presence.removeUserFromRoom(userId, roomId);
        events.publishEvent(new RoomPresenceChangedEvent(ChatEvent.roomRemoved(roomId, false), userId));
        presence.publishRoomMembers(roomId);
    }

    @Transactional
    public void removeMember(long adminId, String roomId, long userId, boolean ban) {
        RoomEntity room = room(roomId);
        requireOwner(room, adminId);
        if (room.getCreatedBy().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The room owner cannot be removed.");
        }
        if (!memberships.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room member not found.");
        }
        if (ban && !bans.existsByRoomIdAndUserId(roomId, userId)) {
            bans.save(new RoomBanEntity(room, user(userId), user(adminId)));
        }
        memberships.deleteByRoomIdAndUserId(roomId, userId);
        presence.removeUserFromRoom(userId, roomId);
        events.publishEvent(new RoomPresenceChangedEvent(ChatEvent.roomRemoved(roomId, false), userId));
        presence.publishRoomMembers(roomId);
    }

    @Transactional
    public void delete(long userId, String roomId) {
        RoomEntity room = room(roomId);
        requireOwner(room, userId);
        List<Long> memberIds = memberships.findAllByRoomId(roomId).stream()
                .map(membership -> membership.getUser().getId()).toList();
        memberIds.forEach(memberId -> events.publishEvent(
                new RoomPresenceChangedEvent(ChatEvent.roomRemoved(roomId, true), memberId)));
        presence.removeRoom(roomId);
        outbox.deleteRoomEvents(roomId);
        receipts.deleteAllByRoomId(roomId);
        messages.deleteAllByRoomId(roomId);
        memberships.deleteByRoomId(roomId);
        bans.deleteByRoomId(roomId);
        rooms.deleteRoomById(roomId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(String roomId, long userId) {
        return memberships.existsByRoomIdAndUserId(roomId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isBanned(String roomId, long userId) {
        return bans.existsByRoomIdAndUserId(roomId, userId);
    }

    @Transactional(readOnly = true)
    public void requireMember(String roomId, long userId) {
        if (!isMember(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room before accessing it.");
        }
    }

    @Transactional(readOnly = true)
    public long memberCount(String roomId) {
        return memberships.countByRoomId(roomId);
    }

    private UserEntity user(long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private RoomEntity room(String id) {
        return rooms.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found."));
    }

    private void requireOwner(RoomEntity room, long userId) {
        if (!room.getCreatedBy().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room owner can manage this room.");
        }
    }

    private RoomResponse toResponse(RoomEntity room, long userId) {
        String role = memberships.findByRoomIdAndUserId(room.getId(), userId)
                .map(membership -> membership.getRole()).orElse(null);
        boolean isOwner = room.getCreatedBy().getId().equals(userId);
        return new RoomResponse(room.getId(), room.getName(), (int) memberCount(room.getId()),
                room.getCreatedBy().getId(), room.isPublic(), isOwner ? room.getInviteCode() : null, role);
    }
}
