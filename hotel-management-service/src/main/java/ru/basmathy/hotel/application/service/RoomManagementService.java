package ru.basmathy.hotel.application.service;

import java.math.BigDecimal;
import org.springframework.data.domain.*;
import ru.basmathy.hotel.presentation.dto.*;

public interface RoomManagementService {
  RoomResponse create(RoomRequest request);

  RoomResponse get(Long id);

  Page<RoomResponse> find(
      Long hotelId,
      Integer minCapacity,
      BigDecimal maxPrice,
      Boolean operational,
      Pageable pageable);

  RoomResponse update(Long id, RoomRequest request);

  void delete(Long id);
}
