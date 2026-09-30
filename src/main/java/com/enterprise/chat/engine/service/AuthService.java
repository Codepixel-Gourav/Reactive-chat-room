package com.enterprise.chat.engine.service;

import com.enterprise.chat.engine.api.AuthDtos.AuthResponse;
import com.enterprise.chat.engine.api.AuthDtos.LoginRequest;
import com.enterprise.chat.engine.api.AuthDtos.RegisterRequest;
import com.enterprise.chat.engine.model.UserEntity;
import com.enterprise.chat.engine.repository.UserRepository;
import com.enterprise.chat.engine.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        UserEntity user = users.save(new UserEntity(email, request.displayName().trim(),
                passwordEncoder.encode(request.password())));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = users.findByEmail(request.email().trim().toLowerCase())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return response(user);
    }

    private AuthResponse response(UserEntity user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.getTtlSeconds(),
                user.getId(), user.getDisplayName());
    }
}
