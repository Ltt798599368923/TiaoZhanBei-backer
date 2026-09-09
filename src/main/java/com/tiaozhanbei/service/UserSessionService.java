package com.tiaozhanbei.service;

import com.tiaozhanbei.entity.User;
import com.tiaozhanbei.entity.UserSession;
import com.tiaozhanbei.repository.UserRepository;
import com.tiaozhanbei.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSessionService {
    private final long sessionTtlHours;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public UserSessionService(@Value("${security.user-session-ttl-hours:168}") long sessionTtlHours,
                              UserRepository userRepository,
                              UserSessionRepository userSessionRepository) {
        this.sessionTtlHours = sessionTtlHours;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        UserSession session = new UserSession();
        session.setToken(token);
        session.setUserId(user.getId());
        session.setCreatedTime(now);
        session.setExpiresAt(now.plusHours(sessionTtlHours));
        userSessionRepository.save(session);
        return token;
    }

    public Optional<Long> resolveUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = userSessionRepository.findById(token).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            userSessionRepository.delete(session);
            return Optional.empty();
        }
        User user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            userSessionRepository.delete(session);
            return Optional.empty();
        }
        return Optional.of(session.getUserId());
    }
}
