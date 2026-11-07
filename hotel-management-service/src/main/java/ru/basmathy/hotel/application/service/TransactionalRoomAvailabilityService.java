package ru.basmathy.hotel.application.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.infrastructure.persistence.*;
import ru.basmathy.hotel.presentation.dto.ReservationResponse;

@Service
@RequiredArgsConstructor
public class TransactionalRoomAvailabilityService implements RoomAvailabilityService {
  private static final Logger log =
      LoggerFactory.getLogger(TransactionalRoomAvailabilityService.class);
  private final RoomRepository rooms;
  private final RoomReservationRepository reservations;

  @Transactional
  public ReservationResponse reserve(Long roomId, String operationId, StayPeriod period) {
    Room room = locked(roomId);
    var existing = reservations.findById(operationId);
    if (existing.isPresent()) {
      var r = existing.get();
      r.verifyIdentity(roomId, period);
      r.verifyHeld();
      return response(r);
    }
    if (!room.isOperational()) throw new DomainConflict("Room is out of service");
    if (reservations.overlaps(roomId, period.getCheckIn(), period.getCheckOut()))
      throw new DomainConflict("Room is already reserved for the period");
    var reservation =
        reservations.save(new RoomReservation(operationId, room, period, ReservationStatus.HELD));
    room.allocate();
    log.info("Slot allocated operation={} room={}", operationId, roomId);
    return response(reservation);
  }

  @Transactional
  public ReservationResponse release(Long roomId, String operationId, StayPeriod period) {
    Room room = locked(roomId);
    var existing = reservations.findById(operationId);
    RoomReservation reservation;
    if (existing.isPresent()) {
      reservation = existing.get();
      reservation.verifyIdentity(roomId, period);
      if (reservation.release()) room.releaseAllocation();
    } else
      reservation =
          reservations.save(
              new RoomReservation(operationId, room, period, ReservationStatus.RELEASED));
    log.info("Slot released operation={} room={}", operationId, roomId);
    return response(reservation);
  }

  private Room locked(Long id) {
    return rooms.findLockedById(id).orElseThrow(() -> new NotFoundException("Room not found"));
  }

  private ReservationResponse response(RoomReservation r) {
    return new ReservationResponse(r.getOperationId(), r.getStatus());
  }
}
