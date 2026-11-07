package ru.basmathy.hotel.configuration;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.infrastructure.persistence.*;

@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {
  private final HotelRepository hotels;
  private final RoomRepository rooms;

  @Transactional
  public void run(String... args) {
    if (hotels.count() > 0) return;
    Hotel h = hotels.save(new Hotel("Basmathy Hotel", "Yekaterinburg", "Lenina 1"));
    rooms.save(new Room(h, "101", 2, new BigDecimal("4500.00")));
    rooms.save(new Room(h, "102", 2, new BigDecimal("5000.00")));
    Room disabled = new Room(h, "103", 3, new BigDecimal("6000.00"));
    disabled.changeOperationalAvailability(false);
    rooms.save(disabled);
  }
}
