package ru.basmathy.hotel.presentation.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ReservationRequest(
    @NotBlank @Size(max = 100) String operationId,
    @NotNull LocalDate checkIn,
    @NotNull LocalDate checkOut) {}
