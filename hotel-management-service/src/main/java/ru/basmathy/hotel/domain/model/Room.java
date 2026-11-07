package ru.basmathy.hotel.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(
    name = "rooms",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_room_number",
            columnNames = {"hotel_id", "number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id", nullable = false)
  private Hotel hotel;

  @Column(nullable = false)
  private String number;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private boolean operational = true;

  @Column(nullable = false)
  private long timesBooked;

  @Version private long version;

  public Room(Hotel hotel, String number, int capacity, BigDecimal price) {
    this.hotel = java.util.Objects.requireNonNull(hotel);
    update(number, capacity, price);
  }

  public void update(String number, int capacity, BigDecimal price) {
    if (number == null || number.isBlank() || capacity < 1 || price == null || price.signum() < 0)
      throw new IllegalArgumentException("Invalid room");
    this.number = number.trim();
    this.capacity = capacity;
    this.price = price;
  }

  public void changeOperationalAvailability(boolean operational) {
    this.operational = operational;
  }

  public void allocate() {
    if (!operational) throw new DomainConflict("Room is out of service");
    timesBooked = Math.addExact(timesBooked, 1);
  }

  public void releaseAllocation() {
    if (timesBooked <= 0) throw new DomainConflict("Reservation counter invariant violated");
    timesBooked--;
  }
}
