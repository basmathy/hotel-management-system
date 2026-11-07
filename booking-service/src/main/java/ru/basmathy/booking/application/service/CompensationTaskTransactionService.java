package ru.basmathy.booking.application.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.infrastructure.persistence.CompensationTaskRepository;

@Service
@RequiredArgsConstructor
public class CompensationTaskTransactionService {
  private final CompensationTaskRepository tasks;

  public record Work(String operationId, Long roomId, StayPeriod period, int attempts) {}

  @Transactional(readOnly = true)
  public Work load(String id) {
    return tasks
        .findById(id)
        .filter(t -> !t.isCompleted())
        .map(t -> new Work(t.getOperationId(), t.getRoomId(), t.getPeriod(), t.getAttempts()))
        .orElse(null);
  }

  @Transactional
  public void record(String id, boolean success) {
    tasks
        .findLockedById(id)
        .ifPresent(
            t -> {
              if (success) t.complete();
              else t.retryLater(Instant.now());
            });
  }
}
