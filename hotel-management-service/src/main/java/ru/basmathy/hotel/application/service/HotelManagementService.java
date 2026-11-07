package ru.basmathy.hotel.application.service;

import org.springframework.data.domain.*;
import ru.basmathy.hotel.presentation.dto.*;

public interface HotelManagementService {
  HotelResponse create(HotelRequest request);

  HotelResponse get(Long id);

  Page<HotelResponse> find(String name, String city, Pageable pageable);

  HotelResponse update(Long id, HotelRequest request);

  void delete(Long id);
}
