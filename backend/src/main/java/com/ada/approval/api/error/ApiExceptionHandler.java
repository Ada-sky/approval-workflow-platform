package com.ada.approval.api.error;

import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Translates REST validation and application failures into the shared API error contract.
 * Unexpected failures receive a sanitized response rather than an internal exception message.
 */
@RestControllerAdvice(basePackages = "com.ada.approval.api.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    /** Mapping failures may occur before Spring has selected an API controller. */
    public static ResponseEntity<ApiErrors.ErrorBody> fallback(
            Exception exception, HttpServletRequest request) {
        int status = 500;
        String message = "An unexpected server error occurred";
        if (exception instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            status = 405;
            message = "HTTP method not supported";
        } else if (exception
                instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {
            status = 415;
            message = "Content type must be application/json";
        } else if (exception instanceof org.springframework.web.servlet.NoHandlerFoundException
                || exception
                        instanceof
                        org.springframework.web.servlet.resource.NoResourceFoundException) {
            status = 404;
            message = "Resource not found";
        }
        return ResponseEntity.status(status)
                .body(new ApiErrors.ErrorBody(status, message, request.getRequestURI()));
    }

    private ResponseEntity<ApiErrors.ErrorBody> error(
            int status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrors.ErrorBody(status, message, request.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> application(ApiException e, HttpServletRequest r) {
        return error(e.getStatus(), e.getMessage(), r);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> forbidden(Exception e, HttpServletRequest r) {
        return error(403, "Access denied", r);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        String message =
                e.getBindingResult().getAllErrors().stream()
                        .map(
                                org.springframework.context.support.DefaultMessageSourceResolvable
                                        ::getDefaultMessage)
                        .sorted()
                        .findFirst()
                        .orElse("Invalid request");
        return error(400, message, r);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<?> malformed(Exception e, HttpServletRequest r) {
        return error(400, "Invalid request values or JSON", r);
    }

    @ExceptionHandler({
        com.ada.approval.exception.ParamException.class,
        org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.dao.OptimisticLockingFailureException.class
    })
    public ResponseEntity<?> conflict(Exception e, HttpServletRequest r) {
        return error(409, "Operation conflicts with existing data or business rules", r);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(Exception e, HttpServletRequest r) {
        return error(405, "HTTP method not supported", r);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> media(Exception e, HttpServletRequest r) {
        return error(415, "Content type must be application/json", r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
        return error(500, "An unexpected server error occurred", r);
    }
}
