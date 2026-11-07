package ru.basmathy.booking.presentation.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String correlationId,
    Map<String, String> validationErrors) {}
