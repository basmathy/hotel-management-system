package ru.basmathy.hotel.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.infrastructure.persistence.*;
import ru.basmathy.hotel.presentation.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomOccupancyStatisticsService {
  private final RoomRepository rooms;
  private final HotelRepository hotels;
  private final RoomReservationRepository reservations;

  public RoomOccupancyStatisticsResponse room(Long roomId, StayPeriod window) {
    return calculate(
        rooms.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found")), window);
  }

  public HotelOccupancyStatisticsResponse hotel(Long hotelId, StayPeriod window) {
    if (!hotels.existsById(hotelId)) throw new NotFoundException("Hotel not found");
    var values = rooms.findByHotelId(hotelId).stream().map(r -> calculate(r, window)).toList();
    long count =
        values.stream()
            .filter(RoomOccupancyStatisticsResponse::operational)
            .mapToLong(RoomOccupancyStatisticsResponse::bookingCount)
            .sum();
    long nights =
        values.stream()
            .filter(RoomOccupancyStatisticsResponse::operational)
            .mapToLong(RoomOccupancyStatisticsResponse::occupiedNights)
            .sum();
    long capacity =
        values.stream().mapToLong(RoomOccupancyStatisticsResponse::capacityNights).sum();
    return new HotelOccupancyStatisticsResponse(
        hotelId,
        count,
        nights,
        capacity,
        capacity == 0 ? null : (double) nights / capacity,
        values);
  }

  private RoomOccupancyStatisticsResponse calculate(Room room, StayPeriod window) {
    var active = reservations.overlapping(room.getId(), window.getCheckIn(), window.getCheckOut());
    long nights = active.stream().mapToLong(r -> r.getPeriod().nightsWithin(window)).sum();
    long capacity = room.isOperational() ? window.nights() : 0;
    return new RoomOccupancyStatisticsResponse(
        room.getId(),
        room.isOperational(),
        active.size(),
        nights,
        capacity,
        capacity == 0 ? null : (double) nights / capacity);
  }
}
