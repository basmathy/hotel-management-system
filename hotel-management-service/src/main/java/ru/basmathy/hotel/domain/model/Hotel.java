package ru.basmathy.hotel.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private String address;

  @Version private long version;

  public Hotel(String name, String city, String address) {
    update(name, city, address);
  }

  public void update(String name, String city, String address) {
    if (name == null
        || name.isBlank()
        || city == null
        || city.isBlank()
        || address == null
        || address.isBlank()) throw new IllegalArgumentException("Hotel fields must not be blank");
    this.name = name.trim();
    this.city = city.trim();
    this.address = address.trim();
  }
}
