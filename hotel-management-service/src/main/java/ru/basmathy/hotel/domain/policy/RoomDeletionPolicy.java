package ru.basmathy.hotel.domain.policy;

import org.springframework.stereotype.Component;
import ru.basmathy.hotel.domain.model.*;

@Component
public class RoomDeletionPolicy {
  public void verify(Room room, boolean hasReservationHistory) {
    if (hasReservationHistory || room.getTimesBooked() > 0)
      throw new DomainConflict("Room has reservation history; disable it instead");
  }
}
