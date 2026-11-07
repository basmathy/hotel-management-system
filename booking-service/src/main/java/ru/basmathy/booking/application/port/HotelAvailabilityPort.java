package ru.basmathy.booking.application.port;

import java.util.List;
import ru.basmathy.booking.domain.model.StayPeriod;

public interface HotelAvailabilityPort {
  record Candidate(Long roomId, Long hotelId) {}

  record HotelRoom(Long id, Long hotelId, boolean operational) {}

  List<Candidate> candidates(Long hotelId, StayPeriod period);

  HotelRoom room(Long roomId);

  void reserve(Long roomId, String operationId, StayPeriod period);

  void release(Long roomId, String operationId, StayPeriod period);
}
