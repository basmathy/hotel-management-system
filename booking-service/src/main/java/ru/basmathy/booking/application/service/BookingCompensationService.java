package ru.basmathy.booking.application.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import ru.basmathy.booking.application.port.HotelAvailabilityPort;

@Service
@RequiredArgsConstructor
public class BookingCompensationService {
  private static final Logger log = LoggerFactory.getLogger(BookingCompensationService.class);
  private final CompensationTaskTransactionService transactions;
  private final HotelAvailabilityPort hotel;

  public void compensate(String operationId) {
    var work = transactions.load(operationId);
    if (work == null) return;
    log.info("Compensation attempt operation={} attempt={}", operationId, work.attempts() + 1);
    boolean released = false;
    try {
      hotel.release(work.roomId(), work.operationId(), work.period());
      released = true;
      log.info("Compensation released operation={}", operationId);
    } catch (RuntimeException e) {
      log.warn(
          "Compensation deferred operation={} cause={}", operationId, e.getClass().getSimpleName());
    }
    transactions.record(operationId, released);
  }
}
