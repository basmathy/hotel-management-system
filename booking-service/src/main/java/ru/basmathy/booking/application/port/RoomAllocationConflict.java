package ru.basmathy.booking.application.port;

import ru.basmathy.booking.domain.model.DomainConflict;

public class RoomAllocationConflict extends DomainConflict {
  public RoomAllocationConflict(String message) {
    super(message);
  }
}
