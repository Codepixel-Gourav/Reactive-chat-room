package com.enterprise.chat.engine.config;

import com.enterprise.chat.engine.security.JwtService;
import com.enterprise.chat.engine.service.RateLimiter;
import com.enterprise.chat.engine.service.RoomApplicationService;
import com.enterprise.chat.engine.service.PresenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final RoomApplicationService rooms;
    private final RateLimiter rateLimit;
    private final PresenceService presence;
    private final List<String> allowedOrigins;

    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Profile("!test")
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(90_000L);
        return container;
    }

    @org.springframework.context.annotation.Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }

    public WebSocketConfig(JwtService jwtService, RoomApplicationService rooms,
                           RateLimiter rateLimit, PresenceService presence,
                           @Value("${chat.allowed-origins}") String origins) {
        this.jwtService = jwtService;
        this.rooms = rooms;
        this.rateLimit = rateLimit;
        this.presence = presence;
        this.allowedOrigins = List.of(origins.split(",")).stream().map(String::trim).toList();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = taskScheduler();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(8).maxPoolSize(32).queueCapacity(2000);
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("A bearer token is required to connect.");
                    }
                    JwtService.TokenClaims claims = jwtService.verify(authorization.substring(7));
                    var principal = new UsernamePasswordAuthenticationToken(
                            Long.toString(claims.userId()), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
                    accessor.setUser(principal);
                    if (accessor.getSessionId() != null) {
                        presence.connect(claims.userId(), accessor.getSessionId());
                    }
                }
                if (StompCommand.DISCONNECT.equals(accessor.getCommand())
                        && accessor.getUser() != null && accessor.getSessionId() != null) {
                    presence.disconnect(Long.parseLong(accessor.getUser().getName()), accessor.getSessionId());
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String userId = accessor.getUser() == null ? null : accessor.getUser().getName();
                    if (destination == null || userId == null) {
                        throw new IllegalArgumentException("Authenticated subscription is required.");
                    }
                    if (destination.startsWith("/user/queue/")) return message;
                    String prefix = "/topic/rooms/";
                    if (!destination.startsWith(prefix) || destination.length() <= prefix.length()) {
                        throw new IllegalArgumentException("Subscription destination is not allowed.");
                    }
                    if (!rooms.isMember(destination.substring(prefix.length()), Long.parseLong(userId))) {
                        throw new IllegalArgumentException("Join the room before subscribing.");
                    }
                }
                String destination = accessor.getDestination();
                if (StompCommand.SEND.equals(accessor.getCommand()) && destination != null
                        && (destination.equals("/app/chat.send") || destination.equals("/app/chat.typing")
                        || destination.equals("/app/chat.receipt") || destination.equals("/app/room.join")
                        || destination.equals("/app/chat.edit") || destination.equals("/app/chat.delete"))) {
                    String userId = accessor.getUser() == null ? null : accessor.getUser().getName();
                    String eventType = destination.endsWith("typing") ? "TYPING"
                            : destination.endsWith("receipt") ? "RECEIPT"
                            : destination.endsWith("edit") || destination.endsWith("delete") ? "MESSAGE_ACTION"
                            : destination.endsWith("room.join") ? "ROOM_JOIN" : "CHAT";
                    if (userId == null || !rateLimit.allow(Long.parseLong(userId), eventType)) {
                        throw new IllegalArgumentException("Rate limit exceeded.");
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                String destination = accessor.getDestination();
                String prefix = "/topic/rooms/";
                if (destination == null || !destination.startsWith(prefix)) return message;
                String userId = accessor.getUser() == null ? null : accessor.getUser().getName();
                String roomId = destination.substring(prefix.length());
                return userId != null && rooms.isMember(roomId, Long.parseLong(userId)) ? message : null;
            }
        });
    }
}
