package com.billing.invoicehub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the application.
 * Centralized error handling for consistent error responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors for request body validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "One or more fields contain invalid values");
        response.put("details", errors);
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle file upload size exceeded errors
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            WebRequest request) {
        log.warn("File upload size exceeded: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.put("error", "File Too Large");
        response.put("message", "The uploaded file exceeds the maximum allowed size (10 MB)");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "Access Denied");
        response.put("message", "You do not have permission to access this resource");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(value = { ResourceNotFoundException.class })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Invalid Request");
        response.put("message", ex.getMessage());
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "An unexpected error occurred. Please contact support.");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
