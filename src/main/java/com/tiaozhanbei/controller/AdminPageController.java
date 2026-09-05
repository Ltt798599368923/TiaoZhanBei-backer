package com.tiaozhanbei.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class AdminPageController {
    static final String ADMIN_SESSION_ATTRIBUTE = "adminAuthenticated";

    @Value("${admin.token:}")
    private String adminToken;

    @GetMapping({"/admin", "/admin/"})
    public void adminPage(HttpServletResponse response) throws IOException {
        redirect(response, "/admin/index.html");
    }

    @PostMapping("/admin/login")
    public void login(@RequestParam(defaultValue = "") String password,
                      HttpSession session,
                      HttpServletResponse response) throws IOException {
        if (matchesConfiguredToken(password)) {
            session.setAttribute(ADMIN_SESSION_ATTRIBUTE, Boolean.TRUE);
            redirect(response, "/admin/index.html?login=success");
            return;
        }
        redirect(response, "/admin/index.html?login=failed");
    }

    @GetMapping("/admin/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        session.invalidate();
        redirect(response, "/admin/index.html");
    }

    private boolean matchesConfiguredToken(String password) {
        return adminToken != null && !adminToken.trim().isEmpty() && adminToken.trim().equals(password);
    }

    private void redirect(HttpServletResponse response, String location) throws IOException {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", location);
    }
}
