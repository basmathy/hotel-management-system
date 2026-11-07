package ru.basmathy.booking.application.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import ru.basmathy.booking.application.port.*;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.presentation.dto.*;

@Service
@RequiredArgsConstructor
public class BookingSagaCoordinator {
  private static final Logger log = LoggerFactory.getLogger(BookingSagaCoordinator.class);
  private final BookingTransactionService transactions;
  private final HotelAvailabilityPort hotel;
  private final BookingCompensationService compensation;

  public BookingResponse execute(UUID bookingId, BookingRequest request) {
    StayPeriod period = new StayPeriod(request.checkIn(), request.checkOut());
    try {
      if (request.roomId() != null) {
        var room = hotel.room(request.roomId());
        if (!room.hotelId().equals(request.hotelId()) || !room.operational())
          throw new DomainConflict("Room does not belong to the hotel or is out of service");
        return allocate(bookingId, request.roomId(), period);
      }
      var candidates = hotel.candidates(request.hotelId(), period);
      for (var candidate : candidates) {
        if (!candidate.hotelId().equals(request.hotelId())) continue;
        try {
          return allocate(bookingId, candidate.roomId(), period);
        } catch (RoomAllocationConflict e) {
          log.info("Candidate conflict booking={} room={}", bookingId, candidate.roomId());
        }
      }
      throw new DomainConflict("No available rooms");
    } catch (RuntimeException e) {
      BookingResponse cancelled = transactions.cancel(bookingId, null, false);
      compensate(cancelled);
      throw e;
    }
  }

  private BookingResponse allocate(UUID id, Long roomId, StayPeriod period) {
    if (!transactions.selectRoom(id, roomId)) {
      var result = transactions.read(id, null, false);
      compensate(result);
      return result;
    }
    String operation = id + "-" + roomId;
    hotel.reserve(roomId, operation, period);
    log.info("Allocation result booking={} room={} allocated=true", id, roomId);
    BookingResponse result = transactions.confirm(id);
    if (result.status() == BookingStatus.CANCELLED) compensate(result);
    return result;
  }

  private void compensate(BookingResponse b) {
    if (b.roomId() != null) compensation.compensate(b.id() + "-" + b.roomId());
  }
}
