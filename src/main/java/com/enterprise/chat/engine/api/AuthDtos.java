package com.enterprise.chat.engine.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() { }
    public record RegisterRequest(@Email @NotBlank @Size(max = 254) String email,
                                  @NotBlank @Size(min = 2, max = 40) String displayName,
                                  @NotBlank @Size(min = 12, max = 72) String password) { }
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }
    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds,
                               long userId, String displayName) { }
}
