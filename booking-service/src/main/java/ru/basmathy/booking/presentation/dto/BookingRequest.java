package ru.basmathy.booking.presentation.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record BookingRequest(
    @NotNull UUID requestId,
    @NotNull @Positive Long hotelId,
    @Positive Long roomId,
    @NotNull LocalDate checkIn,
    @NotNull LocalDate checkOut) {}
