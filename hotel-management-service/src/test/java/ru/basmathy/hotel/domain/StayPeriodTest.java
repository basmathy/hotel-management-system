package ru.basmathy.hotel.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.basmathy.hotel.domain.model.StayPeriod;

class StayPeriodTest {
  private final LocalDate date = LocalDate.of(2030, 1, 1);

  @Test
  void rejectsNullAndEmptyOrReversedPeriods() {
    assertThatThrownBy(() -> new StayPeriod(null, date))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StayPeriod(date, date))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StayPeriod(date.plusDays(1), date))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void adjacentPeriodsDoNotOverlap() {
    var a = new StayPeriod(date, date.plusDays(2));
    var b = new StayPeriod(date.plusDays(2), date.plusDays(4));
    assertThat(a.overlaps(b)).isFalse();
    assertThat(b.overlaps(a)).isFalse();
  }

  @Test
  void intersectsSymmetricallyAndClipsNights() {
    var a = new StayPeriod(date, date.plusDays(5));
    var b = new StayPeriod(date.plusDays(3), date.plusDays(8));
    assertThat(a.overlaps(b)).isTrue();
    assertThat(b.overlaps(a)).isTrue();
    assertThat(a.nightsWithin(b)).isEqualTo(2);
    assertThat(a.nights()).isEqualTo(5);
  }
}
