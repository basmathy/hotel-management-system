package ru.basmathy.booking.domain.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.*;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StayPeriod {
  @Column(name = "check_in", nullable = false)
  private LocalDate checkIn;

  @Column(name = "check_out", nullable = false)
  private LocalDate checkOut;

  public StayPeriod(LocalDate checkIn, LocalDate checkOut) {
    if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn))
      throw new IllegalArgumentException("Check-out must be after check-in");
    this.checkIn = checkIn;
    this.checkOut = checkOut;
  }

  public boolean overlaps(StayPeriod other) {
    return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut);
  }

  public long nights() {
    return ChronoUnit.DAYS.between(checkIn, checkOut);
  }

  public long nightsWithin(StayPeriod window) {
    LocalDate start = checkIn.isAfter(window.checkIn) ? checkIn : window.checkIn;
    LocalDate end = checkOut.isBefore(window.checkOut) ? checkOut : window.checkOut;
    return Math.max(0, ChronoUnit.DAYS.between(start, end));
  }
}
