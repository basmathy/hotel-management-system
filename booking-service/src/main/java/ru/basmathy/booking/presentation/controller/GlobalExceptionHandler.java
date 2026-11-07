package ru.basmathy.booking.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.booking.application.service.NotFoundException;
import ru.basmathy.booking.domain.model.DomainConflict;
import ru.basmathy.booking.presentation.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainConflict.class)
  ResponseEntity<ErrorResponse> conflict(Exception e, HttpServletRequest r) {
    return error(409, "CONFLICT", e.getMessage(), r, Map.of());
  }

  @ExceptionHandler({
    PessimisticLockingFailureException.class,
    ObjectOptimisticLockingFailureException.class,
    DataIntegrityViolationException.class
  })
  ResponseEntity<ErrorResponse> database(Exception e, HttpServletRequest r) {
    return error(
        409,
        "CONCURRENT_CONFLICT",
        "Concurrent operation or duplicate key; retry with the same request ID",
        r,
        Map.of());
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> missing(Exception e, HttpServletRequest r) {
    return error(404, "NOT_FOUND", e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> forbidden(Exception e, HttpServletRequest r) {
    return error(403, "FORBIDDEN", "Access denied", r, Map.of());
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ErrorResponse> unauthorized(Exception e, HttpServletRequest r) {
    return error(401, "UNAUTHORIZED", "Invalid credentials", r, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(v -> fields.put(v.getField(), v.getDefaultMessage()));
    return error(400, "VALIDATION", "Request validation failed", r, fields);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.data.mapping.PropertyReferenceException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<ErrorResponse> bad(Exception e, HttpServletRequest r) {
    return error(400, "BAD_REQUEST", "Invalid request parameters or stay period", r, Map.of());
  }

  @ExceptionHandler(ru.basmathy.booking.application.port.HotelUnavailableException.class)
  ResponseEntity<ErrorResponse> dependency(Exception e, HttpServletRequest r) {
    return error(503, "HOTEL_UNAVAILABLE", "Hotel service is temporarily unavailable", r, Map.of());
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> noResource(Exception e, HttpServletRequest r) {
    return error(404, "NOT_FOUND", "Resource not found", r, Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(Exception e, HttpServletRequest r) {
    return error(405, "METHOD_NOT_ALLOWED", "HTTP method is not supported", r, Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> media(Exception e, HttpServletRequest r) {
    return error(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type", r, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) {
    log.error("Unhandled error correlation={}", MDC.get("correlationId"), e);
    return error(500, "INTERNAL_ERROR", "Unexpected error", r, Map.of());
  }

  private ResponseEntity<ErrorResponse> error(
      int status, String code, String message, HttpServletRequest r, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                r.getRequestURI(),
                MDC.get("correlationId"),
                fields));
  }
}
