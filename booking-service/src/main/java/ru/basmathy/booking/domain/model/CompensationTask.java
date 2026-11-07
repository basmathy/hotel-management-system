package ru.basmathy.booking.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "compensation_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompensationTask {
  @Id private String operationId;

  @Column(nullable = false)
  private UUID bookingId;

  @Column(nullable = false)
  private Long roomId;

  @Embedded private StayPeriod period;

  @Column(nullable = false)
  private boolean completed;

  private int attempts;
  private Instant nextAttemptAt;
  @Version private long version;

  public CompensationTask(Booking booking) {
    operationId = booking.operationId();
    bookingId = booking.getId();
    roomId = booking.getRoomId();
    period = booking.getPeriod();
    nextAttemptAt = Instant.now();
  }

  public void complete() {
    if (!completed) {
      attempts++;
      completed = true;
    }
  }

  public void retryLater(Instant now) {
    if (!completed) {
      attempts++;
      nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    }
  }
}
