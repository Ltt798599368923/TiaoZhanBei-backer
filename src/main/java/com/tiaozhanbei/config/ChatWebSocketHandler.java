package com.tiaozhanbei.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiaozhanbei.service.ConsultationChatHub;
import com.tiaozhanbei.service.ConsultationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConsultationService consultationService;
    private final ConsultationChatHub chatHub;

    public ChatWebSocketHandler(ObjectMapper objectMapper, ConsultationService consultationService,
                                ConsultationChatHub chatHub) {
        this.objectMapper = objectMapper;
        this.consultationService = consultationService;
        this.chatHub = chatHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        chatHub.join(consultationId(session), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        JsonNode payload = objectMapper.readTree(textMessage.getPayload());
        String content = payload.path("content").asText("").trim();
        if (content.isEmpty()) return;
        Long consultationId = consultationId(session);
        String role = String.valueOf(session.getAttributes().get("role"));
        Map<String, Object> message = "admin".equals(role)
                ? consultationService.appendAdminMessage(consultationId, content)
                : consultationService.appendUserMessage((Long) session.getAttributes().get("userId"), consultationId, content);
        chatHub.broadcast(consultationId, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        chatHub.leave(consultationId(session), session);
    }

    private Long consultationId(WebSocketSession session) {
        return (Long) session.getAttributes().get("consultationId");
    }
}
