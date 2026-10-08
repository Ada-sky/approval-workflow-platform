package com.ada.approval.api.error;

/**
 * Carries an intentional API status and client-facing message for expected application failures.
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
