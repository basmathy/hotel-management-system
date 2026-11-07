package ru.basmathy.hotel.presentation.dto;

import org.springframework.stereotype.Component;
import ru.basmathy.hotel.domain.model.Room;

@Component
public class RoomMapper {
  public RoomResponse toResponse(Room value) {
    return new RoomResponse(
        value.getId(),
        value.getHotel().getId(),
        value.getNumber(),
        value.getCapacity(),
        value.getPrice(),
        value.isOperational(),
        value.getTimesBooked());
  }
}
