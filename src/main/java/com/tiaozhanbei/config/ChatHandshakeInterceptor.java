package com.tiaozhanbei.config;

import com.tiaozhanbei.controller.AdminPageController;
import com.tiaozhanbei.service.ConsultationService;
import com.tiaozhanbei.service.UserSessionService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    private final UserSessionService userSessionService;
    private final ConsultationService consultationService;

    public ChatHandshakeInterceptor(UserSessionService userSessionService, ConsultationService consultationService) {
        this.userSessionService = userSessionService;
        this.consultationService = consultationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UriComponents uri = UriComponentsBuilder.fromUri(request.getURI()).build();
        String role = uri.getQueryParams().getFirst("role");
        String consultationIdText = uri.getQueryParams().getFirst("consultationId");
        Long consultationId;
        try {
            consultationId = Long.valueOf(consultationIdText);
        } catch (Exception ignored) {
            return false;
        }

        if ("user".equals(role)) {
            String token = uri.getQueryParams().getFirst("token");
            Optional<Long> userId = userSessionService.resolveUserId("Bearer " + (token == null ? "" : token));
            if (!userId.isPresent() || !consultationService.userOwnsActiveConsultation(userId.get(), consultationId)) {
                return false;
            }
            attributes.put("role", "user");
            attributes.put("userId", userId.get());
        } else if ("admin".equals(role) && request instanceof ServletServerHttpRequest) {
            HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession(false);
            if (session == null || !Boolean.TRUE.equals(session.getAttribute(AdminPageController.ADMIN_SESSION_ATTRIBUTE))
                    || !consultationService.activeConsultationExists(consultationId)) {
                return false;
            }
            attributes.put("role", "admin");
        } else {
            return false;
        }

        attributes.put("consultationId", consultationId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
