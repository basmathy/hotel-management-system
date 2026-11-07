package ru.basmathy.hotel.presentation.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class HotelController {
  private final HotelManagementService hotels;
  private final RoomOccupancyStatisticsService statistics;

  @GetMapping
  public Page<HotelResponse> list(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String city,
      @PageableDefault(size = 20, sort = "id") Pageable page) {
    return hotels.find(name, city, page);
  }

  @GetMapping("/{id}")
  public HotelResponse get(@PathVariable Long id) {
    return hotels.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public HotelResponse create(@Valid @RequestBody HotelRequest r) {
    return hotels.create(r);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest r) {
    return hotels.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    hotels.delete(id);
  }

  @GetMapping("/{id}/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelOccupancyStatisticsResponse statistics(
      @PathVariable Long id, @RequestParam LocalDate checkIn, @RequestParam LocalDate checkOut) {
    return statistics.hotel(id, new StayPeriod(checkIn, checkOut));
  }
}
