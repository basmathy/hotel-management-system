package ru.basmathy.booking.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import ru.basmathy.booking.domain.model.*;

class BookingTest {
  private Booking booking;

  @BeforeEach
  void fixture() {
    User u = new User("tester", "tester@example.test", "hash", UserRole.USER);
    ReflectionTestUtils.setField(u, "id", 7L);
    booking =
        new Booking(
            u,
            UUID.randomUUID(),
            1L,
            2L,
            2L,
            new StayPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3)));
  }

  @Test
  void confirmsIdempotently() {
    assertThat(booking.confirm()).isTrue();
    assertThat(booking.confirm()).isFalse();
    assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
  }

  @Test
  void cancelsPendingIdempotently() {
    assertThat(booking.cancel()).isTrue();
    assertThat(booking.cancel()).isFalse();
    assertThatThrownBy(booking::confirm).isInstanceOf(DomainConflict.class);
  }

  @Test
  void cancelsConfirmedAndPreventsFurtherRoomSelection() {
    booking.confirm();
    booking.cancel();
    assertThatThrownBy(() -> booking.selectRoom(9L)).isInstanceOf(DomainConflict.class);
  }

  @Test
  void ownsOnlyMatchingUserAndChecksReplayParameters() {
    assertThat(booking.isOwnedBy(7L)).isTrue();
    assertThat(booking.isOwnedBy(8L)).isFalse();
    assertThat(booking.matches(1L, 2L, booking.getPeriod())).isTrue();
    assertThat(booking.matches(1L, 3L, booking.getPeriod())).isFalse();
  }

  @Test
  void noPublicStatusSetter() {
    assertThat(Booking.class.getMethods()).noneMatch(m -> m.getName().equals("setStatus"));
  }

  @Test
  void compensationRetryHasBoundedBackoffAndCompletionIsIdempotent() {
    var task = new CompensationTask(booking);
    var now = java.time.Instant.now();
    task.retryLater(now);
    assertThat(task.getNextAttemptAt()).isAfter(now);
    task.complete();
    task.complete();
    assertThat(task.isCompleted()).isTrue();
    assertThat(task.getAttempts()).isEqualTo(2);
  }

  @Test
  void pendingBookingNeedsASelectedRoomBeforeConfirmation() {
    var pending =
        new Booking(booking.getOwner(), UUID.randomUUID(), 1L, null, null, booking.getPeriod());
    assertThatThrownBy(pending::confirm).isInstanceOf(DomainConflict.class);
    pending.selectRoom(3L);
    assertThat(pending.confirm()).isTrue();
  }
}
