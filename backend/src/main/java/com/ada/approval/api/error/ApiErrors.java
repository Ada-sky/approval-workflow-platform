package com.ada.approval.api.error;

import lombok.Data;

import java.time.Instant;

import jakarta.servlet.http.*;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Builds the shared sanitized API error shape without exposing exception internals. */
public final class ApiErrors {
    private ApiErrors() {}

    @Data
    public static class ErrorBody {
        private final String timestamp = Instant.now().toString();
        private final int status;
        private final String error;
        private final String message;
        private final String path;

        public ErrorBody(int status, String message, String path) {
            this.status = status;
            this.error = HttpStatus.valueOf(status).getReasonPhrase();
            this.message = message;
            this.path = path;
        }
    }

    public static void write(
            HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        new ObjectMapper()
                .writeValue(
                        response.getWriter(),
                        new ErrorBody(status, message, request.getRequestURI()));
    }
}
