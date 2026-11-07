package ru.basmathy.booking.application.service;

import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import ru.basmathy.booking.domain.model.StayPeriod;
import ru.basmathy.booking.presentation.dto.*;

@Service
@RequiredArgsConstructor
public class DefaultBookingApplicationService implements BookingApplicationService {
  private final BookingTransactionService transactions;
  private final BookingSagaCoordinator saga;
  private final BookingCancellationService cancellation;

  public BookingResponse create(Long userId, BookingRequest r) {
    Objects.requireNonNull(r.requestId(), "Request ID is required");
    Objects.requireNonNull(r.hotelId(), "Hotel ID is required");
    new StayPeriod(r.checkIn(), r.checkOut());
    if (r.hotelId() < 1 || r.roomId() != null && r.roomId() < 1)
      throw new IllegalArgumentException("IDs must be positive");
    var started = transactions.start(userId, r);
    return started.created() ? saga.execute(started.booking().id(), r) : started.booking();
  }

  public BookingResponse get(UUID id, Long userId, boolean admin) {
    return transactions.read(id, userId, admin);
  }

  public Page<BookingResponse> list(Long userId, boolean admin, Pageable page) {
    return transactions.list(userId, admin, page);
  }

  public BookingResponse cancel(UUID id, Long userId, boolean admin) {
    return cancellation.cancel(id, userId, admin);
  }
}
