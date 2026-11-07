package ru.basmathy.booking.presentation.dto;

import org.springframework.stereotype.Component;
import ru.basmathy.booking.domain.model.Booking;

@Component
public class BookingMapper {
  public BookingResponse toResponse(Booking b) {
    return new BookingResponse(
        b.getId(),
        b.getRequestId(),
        b.getOwner().getId(),
        b.getHotelId(),
        b.getRoomId(),
        b.getPeriod().getCheckIn(),
        b.getPeriod().getCheckOut(),
        b.getStatus(),
        b.getCreatedAt());
  }
}
