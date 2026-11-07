package ru.basmathy.hotel.domain.policy;

import java.util.*;
import org.springframework.stereotype.Component;
import ru.basmathy.hotel.application.port.RoomSelectionStrategy;
import ru.basmathy.hotel.domain.model.Room;

@Component
public class LeastBookedRoomSelectionStrategy implements RoomSelectionStrategy {
  public List<Room> order(List<Room> candidates) {
    return candidates.stream()
        .sorted(Comparator.comparingLong(Room::getTimesBooked).thenComparing(Room::getId))
        .toList();
  }
}
