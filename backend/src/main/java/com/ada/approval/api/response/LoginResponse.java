package com.ada.approval.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preserves the JSON response shape consumed by the session login handlers and existing service
 * adapters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private int code; // Response status code
    private String message; // Response message
    private Object data; // Response data

    public static LoginResponse success() {
        return new LoginResponse(200, "Success", null);
    }

    public static LoginResponse success(String message) {
        return new LoginResponse(200, message, null);
    }

    public static LoginResponse success(String message, Object data) {
        return new LoginResponse(200, message, data);
    }

    public static LoginResponse error() {
        return new LoginResponse(500, "Failure", null);
    }

    public static LoginResponse error(String message) {
        return new LoginResponse(500, message, null);
    }
}
