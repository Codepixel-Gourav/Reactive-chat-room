package com.enterprise.chat.engine.controller;

import com.enterprise.chat.engine.api.ChatDtos.CreateRoomRequest;
import com.enterprise.chat.engine.api.ChatDtos.RoomResponse;
import com.enterprise.chat.engine.api.ChatDtos.RoomSettingsRequest;
import com.enterprise.chat.engine.service.RoomApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomApplicationService rooms;
    public RoomController(RoomApplicationService rooms) { this.rooms = rooms; }

    @GetMapping
    public List<RoomResponse> list(Authentication authentication) {
        return rooms.list(Long.parseLong(authentication.getName()));
    }

    @GetMapping("/public")
    public List<RoomResponse> publicRooms(Authentication authentication) {
        return rooms.publicRooms(Long.parseLong(authentication.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(Authentication authentication, @Valid @RequestBody CreateRoomRequest request) {
        return rooms.create(Long.parseLong(authentication.getName()), request);
    }

    @PostMapping("/{roomId}/join")
    public RoomResponse join(Authentication authentication, @PathVariable String roomId,
                             @RequestParam(required = false) String invite) {
        return rooms.join(Long.parseLong(authentication.getName()), roomId, invite);
    }

    @DeleteMapping("/{roomId}/memberships/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication authentication, @PathVariable String roomId) {
        rooms.leave(Long.parseLong(authentication.getName()), roomId);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable String roomId) {
        rooms.delete(Long.parseLong(authentication.getName()), roomId);
    }

    @PatchMapping("/{roomId}/settings")
    public RoomResponse updateSettings(Authentication authentication, @PathVariable String roomId,
                                       @RequestBody RoomSettingsRequest settings) {
        return rooms.updateSettings(Long.parseLong(authentication.getName()), roomId, settings);
    }

    @PostMapping("/{roomId}/invite/rotate")
    public RoomResponse rotateInvite(Authentication authentication, @PathVariable String roomId) {
        return rooms.rotateInvite(Long.parseLong(authentication.getName()), roomId);
    }

    @DeleteMapping("/{roomId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(Authentication authentication, @PathVariable String roomId, @PathVariable long memberId) {
        rooms.removeMember(Long.parseLong(authentication.getName()), roomId, memberId, false);
    }

    @PostMapping("/{roomId}/members/{memberId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(Authentication authentication, @PathVariable String roomId, @PathVariable long memberId) {
        rooms.removeMember(Long.parseLong(authentication.getName()), roomId, memberId, true);
    }
}
