package ru.basmathy.hotel.application.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.domain.policy.RoomDeletionPolicy;
import ru.basmathy.hotel.infrastructure.persistence.*;
import ru.basmathy.hotel.presentation.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultRoomManagementService implements RoomManagementService {
  private final RoomReservationRepository reservations;
  private final RoomRepository rooms;
  private final HotelRepository hotels;
  private final RoomMapper mapper;
  private final RoomDeletionPolicy deletionPolicy;

  @Transactional
  public RoomResponse create(RoomRequest r) {
    Hotel h =
        hotels
            .findLockedById(r.hotelId())
            .orElseThrow(() -> new NotFoundException("Hotel not found"));
    Room room = new Room(h, r.number(), r.capacity(), r.price());
    room.changeOperationalAvailability(r.operational());
    return mapper.toResponse(rooms.save(room));
  }

  public RoomResponse get(Long id) {
    return mapper.toResponse(
        rooms.findById(id).orElseThrow(() -> new NotFoundException("Room not found")));
  }

  public Page<RoomResponse> find(
      Long hotelId, Integer capacity, BigDecimal maxPrice, Boolean operational, Pageable page) {
    return rooms
        .findAll(
            (root, q, cb) -> {
              var predicates = new ArrayList<Predicate>();
              if (hotelId != null) predicates.add(cb.equal(root.get("hotel").get("id"), hotelId));
              if (capacity != null) predicates.add(cb.ge(root.get("capacity"), capacity));
              if (maxPrice != null) predicates.add(cb.le(root.get("price"), maxPrice));
              if (operational != null)
                predicates.add(cb.equal(root.get("operational"), operational));
              return cb.and(predicates.toArray(Predicate[]::new));
            },
            page)
        .map(mapper::toResponse);
  }

  @Transactional
  public RoomResponse update(Long id, RoomRequest r) {
    Room room = locked(id);
    if (!room.getHotel().getId().equals(r.hotelId()))
      throw new DomainConflict("Cannot move a room to another hotel");
    room.update(r.number(), r.capacity(), r.price());
    room.changeOperationalAvailability(r.operational());
    return mapper.toResponse(room);
  }

  @Transactional
  public void delete(Long id) {
    Room room = locked(id);
    deletionPolicy.verify(room, reservations.existsByRoomId(id));
    rooms.delete(room);
  }

  private Room locked(Long id) {
    return rooms.findLockedById(id).orElseThrow(() -> new NotFoundException("Room not found"));
  }
}
