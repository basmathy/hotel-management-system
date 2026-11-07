package ru.basmathy.booking.application.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.basmathy.booking.presentation.dto.BookingResponse;

@Service
@RequiredArgsConstructor
public class BookingCancellationService {
  private final BookingTransactionService transactions;
  private final BookingCompensationService compensation;

  public BookingResponse cancel(UUID id, Long userId, boolean admin) {
    BookingResponse response = transactions.cancel(id, userId, admin);
    if (response.roomId() != null) compensation.compensate(response.id() + "-" + response.roomId());
    return response;
  }
}
