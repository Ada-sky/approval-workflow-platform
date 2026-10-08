package com.ada.approval.api.error;

import com.ada.approval.api.error.ApiErrors;
import com.ada.approval.api.error.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles failures outside the scoped REST advice, including requests without a selected
 * controller. Retained as an API fallback rather than a legacy page-rendering exception handler.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrors.ErrorBody> accessDenied(HttpServletRequest request) {
        return ResponseEntity.status(403)
                .body(new ApiErrors.ErrorBody(403, "Access denied", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrors.ErrorBody> globExceptionHandler(
            Exception exception, HttpServletRequest request) {
        return ApiExceptionHandler.fallback(exception, request);
    }
}
