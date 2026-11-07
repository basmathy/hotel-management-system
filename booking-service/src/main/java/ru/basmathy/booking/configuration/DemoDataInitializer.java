package ru.basmathy.booking.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.infrastructure.persistence.UserRepository;

@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;

  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    users.save(
        new User("admin", "admin@example.test", passwords.encode("AdminDemo123!"), UserRole.ADMIN));
    users.save(
        new User("guest", "guest@example.test", passwords.encode("GuestDemo123!"), UserRole.USER));
  }
}
