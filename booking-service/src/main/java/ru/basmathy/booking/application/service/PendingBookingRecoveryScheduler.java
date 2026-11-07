package ru.basmathy.booking.application.service;

import java.time.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.basmathy.booking.infrastructure.persistence.BookingRepository;

@Component
@ConditionalOnProperty(
    name = "saga.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PendingBookingRecoveryScheduler {
  private static final Logger log = LoggerFactory.getLogger(PendingBookingRecoveryScheduler.class);
  private final BookingRepository bookings;
  private final BookingTransactionService transactions;
  private final Duration age;

  public PendingBookingRecoveryScheduler(
      BookingRepository bookings,
      BookingTransactionService transactions,
      @Value("${saga.recovery-age:PT2M}") Duration age) {
    this.bookings = bookings;
    this.transactions = transactions;
    this.age = age;
  }

  @Scheduled(fixedDelayString = "${saga.recovery-delay-ms:30000}")
  public void run() {
    Instant before = Instant.now().minus(age);
    for (UUID id : bookings.findStale(before, PageRequest.of(0, 100)))
      try {
        transactions.recover(id, before);
      } catch (RuntimeException e) {
        log.warn("Recovery deferred booking={} cause={}", id, e.getClass().getSimpleName());
      }
  }
}
