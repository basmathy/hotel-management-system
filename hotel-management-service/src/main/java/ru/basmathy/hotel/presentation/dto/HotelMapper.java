package ru.basmathy.hotel.presentation.dto;

import org.springframework.stereotype.Component;
import ru.basmathy.hotel.domain.model.Hotel;

@Component
public class HotelMapper {
  public HotelResponse toResponse(Hotel value) {
    return new HotelResponse(value.getId(), value.getName(), value.getCity(), value.getAddress());
  }
}
