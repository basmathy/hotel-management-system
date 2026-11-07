package ru.basmathy.hotel.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.domain.policy.HotelDeletionPolicy;
import ru.basmathy.hotel.infrastructure.persistence.*;
import ru.basmathy.hotel.presentation.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultHotelManagementService implements HotelManagementService {
  private final RoomRepository rooms;
  private final HotelRepository hotels;
  private final HotelMapper mapper;
  private final HotelDeletionPolicy deletionPolicy;

  @Transactional
  public HotelResponse create(HotelRequest r) {
    return mapper.toResponse(hotels.save(new Hotel(r.name(), r.city(), r.address())));
  }

  public HotelResponse get(Long id) {
    return mapper.toResponse(
        hotels.findById(id).orElseThrow(() -> new NotFoundException("Hotel not found")));
  }

  public Page<HotelResponse> find(String name, String city, Pageable page) {
    return hotels
        .findAll(
            (root, q, cb) ->
                cb.and(
                    name == null
                        ? cb.conjunction()
                        : cb.like(
                            cb.lower(root.get("name")),
                            "%" + name.toLowerCase(java.util.Locale.ROOT) + "%"),
                    city == null
                        ? cb.conjunction()
                        : cb.equal(
                            cb.lower(root.get("city")), city.toLowerCase(java.util.Locale.ROOT))),
            page)
        .map(mapper::toResponse);
  }

  @Transactional
  public HotelResponse update(Long id, HotelRequest r) {
    Hotel h = locked(id);
    h.update(r.name(), r.city(), r.address());
    return mapper.toResponse(h);
  }

  @Transactional
  public void delete(Long id) {
    Hotel h = locked(id);
    deletionPolicy.verify(h, rooms.existsByHotelId(id));
    hotels.delete(h);
  }

  private Hotel locked(Long id) {
    return hotels.findLockedById(id).orElseThrow(() -> new NotFoundException("Hotel not found"));
  }
}
