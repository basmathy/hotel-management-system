package ru.basmathy.booking.domain.policy;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import ru.basmathy.booking.domain.model.Booking;

@Component
public class BookingAccessPolicy {
  public void verify(Booking booking, Long userId, boolean administrator) {
    if (!administrator && !booking.isOwnedBy(userId))
      throw new AccessDeniedException("Booking belongs to another user");
  }
}
