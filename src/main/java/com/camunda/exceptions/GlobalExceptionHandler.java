package com.camunda.exceptions;

import io.camunda.client.api.command.ClientStatusException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 – Bean Validation (@Valid on @RequestBody) ──────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.warn("[GlobalExceptionHandler] Validation failed: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    // ─── 400 – @Validated path/query param constraint violations ─────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(
            ConstraintViolationException ex, WebRequest request) {

        List<String> errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .toList();

        log.warn("[GlobalExceptionHandler] Constraint violation: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "Constraint violation", errors, request);
    }

    // ─── 503 – Zeebe / Camunda gRPC communication failure ────────────────────
    @ExceptionHandler(ClientStatusException.class)
    public ResponseEntity<Map<String, Object>> handlecamundaClient(
            ClientStatusException ex, WebRequest request) {

        log.error("[GlobalExceptionHandler] Zeebe client error: status={} message={}",
                ex.getStatusCode(), ex.getMessage());
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Camunda / Zeebe is unavailable: " + ex.getMessage(),
                List.of(),
                request);
    }

    // ─── 404 – Process instance / resource not found ──────────────────────────
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(
            OrderNotFoundException ex, WebRequest request) {

        log.warn("[GlobalExceptionHandler] Order not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), request);
    }

    // ─── 409 – Duplicate / conflicting order state ────────────────────────────
    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<Map<String, Object>> handleOrderConflict(
            OrderConflictException ex, WebRequest request) {

        log.warn("[GlobalExceptionHandler] Order conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of(), request);
    }

    // ─── 500 – Any other unhandled runtime exception ──────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex, WebRequest request) {

        log.error("[GlobalExceptionHandler] Unhandled exception: {}", ex.getMessage(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.",
                List.of(),
                request);
    }

    // ─── helper ───────────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String message, List<String> errors, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("path",      request.getDescription(false).replace("uri=", ""));
        if (!errors.isEmpty()) {
            body.put("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
