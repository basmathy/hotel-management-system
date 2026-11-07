package ru.basmathy.hotel.application.port;

import java.util.List;
import ru.basmathy.hotel.domain.model.Room;

public interface RoomSelectionStrategy {
  List<Room> order(List<Room> candidates);
}
