package ru.basmathy.hotel.application.service;

import ru.basmathy.hotel.domain.model.StayPeriod;
import ru.basmathy.hotel.presentation.dto.ReservationResponse;

public interface RoomAvailabilityService {
  ReservationResponse reserve(Long roomId, String operationId, StayPeriod period);

  ReservationResponse release(Long roomId, String operationId, StayPeriod period);
}
