package com.enterprise.chat.engine.security;

import com.enterprise.chat.engine.model.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final byte[] key;
    private final long ttlSeconds;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${chat.jwt.secret}") String secret,
                      @Value("${chat.jwt.ttl:PT15M}") java.time.Duration ttl) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes.");
        }
        this.objectMapper = objectMapper;
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttl.toSeconds();
    }

    public String issue(UserEntity user) {
        try {
            Instant now = Instant.now();
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.getId().toString(),
                    "email", user.getEmail(),
                    "name", user.getDisplayName(),
                    "role", user.getRole().name(),
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(ttlSeconds).getEpochSecond())));
            String unsigned = header + "." + payload;
            return unsigned + "." + ENCODER.encodeToString(sign(unsigned));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not issue access token.", ex);
        }
    }

    public TokenClaims verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Malformed access token.");
            Map<?, ?> header = objectMapper.readValue(DECODER.decode(parts[0]), Map.class);
            if (!"HS256".equals(header.get("alg"))) throw new IllegalArgumentException("Unsupported token algorithm.");
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = DECODER.decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("Invalid access token signature.");
            }
            Map<?, ?> claims = objectMapper.readValue(DECODER.decode(parts[1]), Map.class);
            long expires = ((Number) claims.get("exp")).longValue();
            if (expires <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("Access token expired.");
            return new TokenClaims(Long.parseLong(String.valueOf(claims.get("sub"))),
                    String.valueOf(claims.get("email")), String.valueOf(claims.get("role")));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid access token.", ex);
        }
    }

    public long getTtlSeconds() { return ttlSeconds; }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(byte[] bytes) { return ENCODER.encodeToString(bytes); }

    public record TokenClaims(long userId, String email, String role) { }
}
