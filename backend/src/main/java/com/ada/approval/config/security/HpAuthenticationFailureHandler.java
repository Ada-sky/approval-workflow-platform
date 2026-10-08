package com.ada.approval.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ada.approval.api.response.LoginResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Returns the existing sanitized JSON login failure without disclosing account credentials. */
@Component
public class HpAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException, ServletException {
        // Login success handler
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(
                "application/json;charset=utf-8"); // Return a JSON response to the browser
        LoginResponse respBean = LoginResponse.error("Invalid username or password");
        // Spring Boot provides Jackson JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(respBean);
        // Return JSON
        response.getWriter().write(json);
    }
}
