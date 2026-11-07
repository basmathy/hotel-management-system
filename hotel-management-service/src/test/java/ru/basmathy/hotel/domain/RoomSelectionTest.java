package ru.basmathy.hotel.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.domain.policy.LeastBookedRoomSelectionStrategy;

class RoomSelectionTest {
  @Test
  void sortsByCounterThenIdWithoutChangingInput() {
    Room a = mock(Room.class), b = mock(Room.class), c = mock(Room.class);
    when(a.getId()).thenReturn(3L);
    when(a.getTimesBooked()).thenReturn(2L);
    when(b.getId()).thenReturn(2L);
    when(b.getTimesBooked()).thenReturn(1L);
    when(c.getId()).thenReturn(1L);
    when(c.getTimesBooked()).thenReturn(1L);
    var input = List.of(a, b, c);
    assertThat(new LeastBookedRoomSelectionStrategy().order(input)).containsExactly(c, b, a);
    assertThat(input).containsExactly(a, b, c);
  }

  @Test
  void releaseIsIdempotentAndIdentityIsImmutable() {
    Room room = new Room(new Hotel("H", "C", "A"), "1", 1, BigDecimal.ONE);
    ReflectionTestUtils.setField(room, "id", 1L);
    var period = new StayPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));
    var r = new RoomReservation("op", room, period, ReservationStatus.HELD);
    assertThat(r.release()).isTrue();
    assertThat(r.release()).isFalse();
    assertThatThrownBy(r::verifyHeld).isInstanceOf(DomainConflict.class);
    assertThatThrownBy(() -> r.verifyIdentity(2L, period)).isInstanceOf(DomainConflict.class);
  }

  @Test
  void operationalStateAndCounterCannotBeBypassed() {
    var room = new Room(new Hotel("H", "C", "A"), "1", 1, BigDecimal.ONE);
    assertThatThrownBy(room::releaseAllocation).isInstanceOf(DomainConflict.class);
    room.allocate();
    room.releaseAllocation();
    assertThat(room.getTimesBooked()).isZero();
    room.changeOperationalAvailability(false);
    assertThatThrownBy(room::allocate).isInstanceOf(DomainConflict.class);
  }
}
