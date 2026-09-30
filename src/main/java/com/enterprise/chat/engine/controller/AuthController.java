package com.enterprise.chat.engine.controller;

import com.enterprise.chat.engine.api.AuthDtos.AuthResponse;
import com.enterprise.chat.engine.api.AuthDtos.LoginRequest;
import com.enterprise.chat.engine.api.AuthDtos.RegisterRequest;
import com.enterprise.chat.engine.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
