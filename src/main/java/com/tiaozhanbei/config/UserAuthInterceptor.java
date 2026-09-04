package com.tiaozhanbei.config;

import com.tiaozhanbei.service.UserSessionService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {
    private static final Pattern USER_SCOPED_PATH = Pattern.compile(
            "^/api/(?:user/(?:info|update)|favorite/(?:list|add|remove)|consultation/(?:list|create|detail|delete)|contract/(?:list|create|upload|detail|delete|file)|notice/user)/(\\d+)(?:/.*)?$");

    private final UserSessionService userSessionService;

    public UserAuthInterceptor(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Optional<Long> userId = userSessionService.resolveUserId(request.getHeader("Authorization"));
        if (!userId.isPresent()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher matcher = USER_SCOPED_PATH.matcher(path);
        if (matcher.matches() && !userId.get().equals(Long.valueOf(matcher.group(1)))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return false;
        }
        request.setAttribute("currentUserId", userId.get());
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
