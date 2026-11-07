package ru.basmathy.booking.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.basmathy.booking.infrastructure.persistence.CompensationTaskRepository;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "saga.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CompensationRetryScheduler {
  private static final Logger log = LoggerFactory.getLogger(CompensationRetryScheduler.class);
  private final CompensationTaskRepository tasks;
  private final BookingCompensationService compensation;

  @Scheduled(fixedDelayString = "${saga.retry-delay-ms:10000}")
  public void run() {
    for (String id : tasks.findDue(Instant.now(), PageRequest.of(0, 100)))
      try {
        compensation.compensate(id);
      } catch (RuntimeException e) {
        log.warn("Retry deferred operation={} cause={}", id, e.getClass().getSimpleName());
      }
  }
}
