package ru.basmathy.hotel.presentation.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.hotel.application.service.*;
import ru.basmathy.hotel.domain.model.StayPeriod;
import ru.basmathy.hotel.presentation.dto.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class RoomController {
  private final RoomManagementService rooms;
  private final RoomRecommendationService recommendations;
  private final RoomOccupancyStatisticsService statistics;

  @GetMapping
  public Page<RoomResponse> list(
      @RequestParam(required = false) Long hotelId,
      @RequestParam(required = false) Integer minCapacity,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(required = false) Boolean operational,
      @PageableDefault(size = 20, sort = "id") Pageable page) {
    if (minCapacity != null && minCapacity < 1 || maxPrice != null && maxPrice.signum() < 0)
      throw new IllegalArgumentException("Invalid filters");
    return rooms.find(hotelId, minCapacity, maxPrice, operational, page);
  }

  @GetMapping("/{id}")
  public RoomResponse get(@PathVariable Long id) {
    return rooms.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public RoomResponse create(@Valid @RequestBody RoomRequest r) {
    return rooms.create(r);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest r) {
    return rooms.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    rooms.delete(id);
  }

  @GetMapping("/recommendations")
  public List<RoomRecommendationResponse> recommendations(
      @RequestParam(required = false) Long hotelId,
      @RequestParam(defaultValue = "1") int capacity,
      @RequestParam LocalDate checkIn,
      @RequestParam LocalDate checkOut) {
    return recommendations.recommend(hotelId, capacity, new StayPeriod(checkIn, checkOut));
  }

  @GetMapping("/{id}/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomOccupancyStatisticsResponse statistics(
      @PathVariable Long id, @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut) {
    return statistics.room(id, new StayPeriod(checkIn, checkOut));
  }
}
