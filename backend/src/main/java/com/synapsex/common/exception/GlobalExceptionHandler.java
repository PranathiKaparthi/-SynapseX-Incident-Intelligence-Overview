package com.synapsex.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailDto> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ProblemDetailDto.FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ProblemDetailDto.FieldErrorDto(error.getField(), error.getDefaultMessage()))
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Request payload failed validation checks",
                request.getRequestURI(),
                "VALIDATION_ERROR",
                errors
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailDto> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                "BAD_REQUEST",
                List.of()
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ProblemDetailDto> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {
        log.warn("Security violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI(),
                "SECURITY_VIOLATION",
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailDto> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "Access denied for current user or tenant boundary",
                request.getRequestURI(),
                "ACCESS_DENIED",
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailDto> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected server error occurred",
                request.getRequestURI(),
                "INTERNAL_ERROR",
                List.of()
        );
    }

    private ResponseEntity<ProblemDetailDto> buildResponse(
            HttpStatus status, String title, String detail, String uri, String code,
            List<ProblemDetailDto.FieldErrorDto> fieldErrors) {

        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        ProblemDetailDto body = new ProblemDetailDto(
                "https://synapsex.io/errors/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                uri,
                code,
                traceId,
                Instant.now(),
                fieldErrors
        );

        return ResponseEntity.status(status).body(body);
    }
}
