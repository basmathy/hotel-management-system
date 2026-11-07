package ru.basmathy.hotel.domain.model;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.*;

@Entity
@Table(
    name = "room_reservations",
    indexes =
        @Index(name = "idx_reservation_overlap", columnList = "room_id,status,check_in,check_out"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomReservation {
  @Id
  @Column(length = 100)
  private String operationId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Embedded private StayPeriod period;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status;

  public RoomReservation(
      String operationId, Room room, StayPeriod period, ReservationStatus status) {
    if (operationId == null || operationId.isBlank() || operationId.length() > 100)
      throw new IllegalArgumentException("Invalid operation id");
    this.operationId = operationId;
    this.room = Objects.requireNonNull(room);
    this.period = Objects.requireNonNull(period);
    this.status = Objects.requireNonNull(status);
  }

  public void verifyIdentity(Long roomId, StayPeriod period) {
    if (!room.getId().equals(roomId) || !this.period.equals(period))
      throw new DomainConflict("Operation key reused with different parameters");
  }

  public boolean release() {
    if (status == ReservationStatus.RELEASED) return false;
    status = ReservationStatus.RELEASED;
    return true;
  }

  public void verifyHeld() {
    if (status != ReservationStatus.HELD)
      throw new DomainConflict("Operation was already released");
  }
}
