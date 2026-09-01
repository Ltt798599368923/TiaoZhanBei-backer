package com.tiaozhanbei.service;

import com.tiaozhanbei.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long sessionTtlHours;

    public UserSessionService(@Value("${security.user-session-ttl-hours:168}") long sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(user.getId(), Instant.now().plus(sessionTtlHours, ChronoUnit.HOURS)));
        return token;
    }

    public Optional<Long> resolveUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.userId);
    }

    private static class Session {
        private final Long userId;
        private final Instant expiresAt;

        private Session(Long userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
