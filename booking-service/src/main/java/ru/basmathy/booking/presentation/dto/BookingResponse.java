package ru.basmathy.booking.presentation.dto;

import java.time.*;
import java.util.UUID;
import ru.basmathy.booking.domain.model.BookingStatus;

public record BookingResponse(
    UUID id,
    UUID requestId,
    Long userId,
    Long hotelId,
    Long roomId,
    LocalDate checkIn,
    LocalDate checkOut,
    BookingStatus status,
    Instant createdAt) {}
