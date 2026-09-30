package com.enterprise.chat.engine.api;

import jakarta.validation.constraints.Size;

public final class SocketDtos {
    private SocketDtos() { }
    public record ClientEvent(String type,
                              @Size(max = 80) String roomId,
                              @Size(max = 36) String clientMessageId,
                              @Size(max = 36) String messageId,
                              @Size(max = 4000) String content,
                              String state,
                              @Size(max = 36) String inviteCode) { }
}
