package ru.basmathy.hotel.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.application.port.RoomSelectionStrategy;
import ru.basmathy.hotel.domain.model.StayPeriod;
import ru.basmathy.hotel.infrastructure.persistence.RoomRepository;
import ru.basmathy.hotel.presentation.dto.RoomRecommendationResponse;

@Service
@RequiredArgsConstructor
public class RoomRecommendationService {
  private final RoomRepository rooms;
  private final RoomSelectionStrategy strategy;

  @Transactional(readOnly = true)
  public List<RoomRecommendationResponse> recommend(Long hotelId, int capacity, StayPeriod period) {
    if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
    return strategy
        .order(rooms.available(hotelId, capacity, period.getCheckIn(), period.getCheckOut()))
        .stream()
        .map(
            r ->
                new RoomRecommendationResponse(
                    r.getId(),
                    r.getHotel().getId(),
                    r.getNumber(),
                    r.getTimesBooked(),
                    r.getPrice()))
        .toList();
  }
}
