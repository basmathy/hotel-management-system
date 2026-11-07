package ru.basmathy.booking.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_booking_request",
            columnNames = {"owner_id", "request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(nullable = false)
  private Long hotelId;

  private Long requestedRoomId;
  private Long roomId;
  @Embedded private StayPeriod period;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BookingStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  @Version private long version;

  public Booking(
      User owner,
      UUID requestId,
      Long hotelId,
      Long requestedRoomId,
      Long roomId,
      StayPeriod period) {
    this.id = UUID.randomUUID();
    this.owner = Objects.requireNonNull(owner);
    this.requestId = Objects.requireNonNull(requestId);
    this.hotelId = Objects.requireNonNull(hotelId);
    this.requestedRoomId = requestedRoomId;
    this.roomId = roomId;
    this.period = Objects.requireNonNull(period);
    this.status = BookingStatus.PENDING;
    this.createdAt = Instant.now();
  }

  public boolean confirm() {
    if (status == BookingStatus.CONFIRMED) return false;
    if (status != BookingStatus.PENDING)
      throw new DomainConflict("Cancelled booking cannot be confirmed");
    if (roomId == null) throw new DomainConflict("Cannot confirm without a selected room");
    status = BookingStatus.CONFIRMED;
    return true;
  }

  public boolean cancel() {
    if (status == BookingStatus.CANCELLED) return false;
    status = BookingStatus.CANCELLED;
    return true;
  }

  public void selectRoom(Long roomId) {
    if (status != BookingStatus.PENDING) throw new DomainConflict("Booking is no longer pending");
    if (requestedRoomId != null && !requestedRoomId.equals(roomId))
      throw new DomainConflict("Cannot change explicitly requested room");
    this.roomId = Objects.requireNonNull(roomId);
  }

  public boolean isOwnedBy(Long userId) {
    return owner.getId().equals(userId);
  }

  public boolean matches(Long hotelId, Long requestedRoomId, StayPeriod period) {
    return this.hotelId.equals(hotelId)
        && Objects.equals(this.requestedRoomId, requestedRoomId)
        && this.period.equals(period);
  }

  public String operationId() {
    return id + "-" + roomId;
  }
}
