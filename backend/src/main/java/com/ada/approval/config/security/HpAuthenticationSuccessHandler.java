package com.ada.approval.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ada.approval.api.response.LoginResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Returns the existing JSON login result for session-based frontend authentication. */
@Component
public class HpAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        // Login success handler
        response.setContentType(
                "application/json;charset=utf-8"); // Return a JSON response to the browser
        LoginResponse respBean = LoginResponse.success("Login successful");
        // Spring Boot provides Jackson JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(respBean);
        // Return JSON
        response.getWriter().write(json);
    }
}
