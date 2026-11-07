package ru.basmathy.hotel.domain.policy;

import org.springframework.stereotype.Component;
import ru.basmathy.hotel.domain.model.*;

@Component
public class HotelDeletionPolicy {
  public void verify(Hotel hotel, boolean containsRooms) {
    java.util.Objects.requireNonNull(hotel);
    if (containsRooms) throw new DomainConflict("Hotel still contains rooms");
  }
}
