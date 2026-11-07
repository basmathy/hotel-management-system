package ru.basmathy.hotel.presentation.dto;

import ru.basmathy.hotel.domain.model.ReservationStatus;

public record ReservationResponse(String operationId, ReservationStatus status) {}
