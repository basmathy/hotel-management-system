package ru.basmathy.booking.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.basmathy.booking.application.port.*;
import ru.basmathy.booking.application.service.*;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.presentation.dto.*;

@ExtendWith(MockitoExtension.class)
class BookingSagaCoordinatorTest {
  @Mock BookingTransactionService transactions;
  @Mock HotelAvailabilityPort hotel;
  @Mock BookingCompensationService compensation;
  @InjectMocks BookingSagaCoordinator saga;
  UUID id = UUID.randomUUID();
  LocalDate start = LocalDate.of(2030, 1, 1);
  BookingRequest request = new BookingRequest(UUID.randomUUID(), 1L, 2L, start, start.plusDays(2));

  BookingResponse response(BookingStatus status, Long room) {
    return new BookingResponse(
        id, request.requestId(), 1L, 1L, room, start, start.plusDays(2), status, Instant.now());
  }

  @Test
  void successfulAllocationConfirms() {
    when(hotel.room(2L)).thenReturn(new HotelAvailabilityPort.HotelRoom(2L, 1L, true));
    when(transactions.selectRoom(id, 2L)).thenReturn(true);
    when(transactions.confirm(id)).thenReturn(response(BookingStatus.CONFIRMED, 2L));
    assertThat(saga.execute(id, request).status()).isEqualTo(BookingStatus.CONFIRMED);
    var ordered = inOrder(hotel, transactions);
    ordered.verify(hotel).room(2L);
    ordered.verify(transactions).selectRoom(id, 2L);
    ordered.verify(hotel).reserve(eq(2L), eq(id + "-2"), any());
    ordered.verify(transactions).confirm(id);
    verifyNoInteractions(compensation);
  }

  @Test
  void hotelFailureCancelsAndCompensates() {
    when(hotel.room(2L)).thenThrow(new HotelUnavailableException("down", null));
    when(transactions.cancel(id, null, false)).thenReturn(response(BookingStatus.CANCELLED, 2L));
    assertThatThrownBy(() -> saga.execute(id, request))
        .isInstanceOf(HotelUnavailableException.class);
    verify(compensation).compensate(id + "-2");
    verify(transactions, never()).confirm(any());
  }

  @Test
  void autoSelectionMovesToNextConflictingCandidate() {
    request = new BookingRequest(request.requestId(), 1L, null, start, start.plusDays(2));
    when(hotel.candidates(eq(1L), any()))
        .thenReturn(
            List.of(
                new HotelAvailabilityPort.Candidate(2L, 1L),
                new HotelAvailabilityPort.Candidate(3L, 1L)));
    when(transactions.selectRoom(eq(id), anyLong())).thenReturn(true);
    doThrow(new RoomAllocationConflict("busy")).when(hotel).reserve(eq(2L), anyString(), any());
    when(transactions.confirm(id)).thenReturn(response(BookingStatus.CONFIRMED, 3L));
    assertThat(saga.execute(id, request).roomId()).isEqualTo(3L);
    verify(hotel).reserve(eq(3L), eq(id + "-3"), any());
  }

  @Test
  void recoveredBookingIsNeverReconfirmed() {
    when(hotel.room(2L)).thenReturn(new HotelAvailabilityPort.HotelRoom(2L, 1L, true));
    when(transactions.selectRoom(id, 2L)).thenReturn(true);
    when(transactions.confirm(id)).thenReturn(response(BookingStatus.CANCELLED, 2L));
    assertThat(saga.execute(id, request).status()).isEqualTo(BookingStatus.CANCELLED);
    verify(compensation).compensate(id + "-2");
  }
}
