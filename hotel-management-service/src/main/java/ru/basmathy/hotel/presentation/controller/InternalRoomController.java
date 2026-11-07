package ru.basmathy.hotel.presentation.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.hotel.application.service.*;
import ru.basmathy.hotel.domain.model.StayPeriod;
import ru.basmathy.hotel.presentation.dto.*;

@RestController
@RequestMapping("/internal/rooms")
@PreAuthorize("hasAuthority('INTERNAL')")
@RequiredArgsConstructor
public class InternalRoomController {
  private final RoomAvailabilityService availability;
  private final RoomRecommendationService recommendations;
  private final RoomManagementService rooms;

  @GetMapping("/available")
  public List<RoomRecommendationResponse> available(
      @RequestParam Long hotelId,
      @RequestParam LocalDate checkIn,
      @RequestParam LocalDate checkOut) {
    return recommendations.recommend(hotelId, 1, new StayPeriod(checkIn, checkOut));
  }

  @GetMapping("/{id}")
  public RoomResponse get(@PathVariable Long id) {
    return rooms.get(id);
  }

  @PostMapping("/{id}/reservations")
  public ReservationResponse reserve(
      @PathVariable Long id, @Valid @RequestBody ReservationRequest r) {
    return availability.reserve(id, r.operationId(), new StayPeriod(r.checkIn(), r.checkOut()));
  }

  @PostMapping("/{id}/reservations/release")
  public ReservationResponse release(
      @PathVariable Long id, @Valid @RequestBody ReservationRequest r) {
    return availability.release(id, r.operationId(), new StayPeriod(r.checkIn(), r.checkOut()));
  }
}
