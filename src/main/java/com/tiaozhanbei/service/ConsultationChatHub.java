package com.tiaozhanbei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ConsultationChatHub {
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public ConsultationChatHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void join(Long consultationId, WebSocketSession session) {
        sessions.computeIfAbsent(consultationId, key -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void leave(Long consultationId, WebSocketSession session) {
        Set<WebSocketSession> room = sessions.get(consultationId);
        if (room == null) return;
        room.remove(session);
        if (room.isEmpty()) sessions.remove(consultationId, room);
    }

    public void broadcast(Long consultationId, Map<String, Object> message) {
        Set<WebSocketSession> room = sessions.get(consultationId);
        if (room == null) return;
        try {
            String payload = objectMapper.writeValueAsString(message);
            for (WebSocketSession session : room) {
                if (!session.isOpen()) {
                    leave(consultationId, session);
                    continue;
                }
                synchronized (session) {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(payload));
                }
            }
        } catch (IOException ignored) {
            // A transient client socket failure must not affect message persistence.
        }
    }
}
