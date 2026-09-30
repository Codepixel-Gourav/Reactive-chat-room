package com.enterprise.chat.engine.security;

import com.enterprise.chat.engine.service.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter rateLimit;
    private final int maxPerMinute;

    public AuthRateLimitFilter(RateLimiter rateLimit,
                               @Value("${chat.rate-limit.handshake-per-minute:20}") int maxPerMinute) {
        this.rateLimit = rateLimit;
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/auth/") || path.equals("/ws") || path.startsWith("/ws/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String subject = request.getRemoteAddr();
        if (!rateLimit.allow(subject, "entry", maxPerMinute)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many authentication or connection attempts.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
