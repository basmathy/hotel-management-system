package ru.basmathy.booking.domain.model;

import jakarta.persistence.*;
import java.util.Locale;
import lombok.*;

@Entity
@Table(name = "app_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 80)
  private String username;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Version private long version;

  public User(String username, String email, String passwordHash, UserRole role) {
    if (username == null
        || username.isBlank()
        || passwordHash == null
        || passwordHash.isBlank()
        || role == null) throw new IllegalArgumentException("Invalid user");
    this.username = username.trim().toLowerCase(Locale.ROOT);
    update(email, passwordHash, role);
  }

  public void update(String email, String passwordHash, UserRole role) {
    if (email != null) {
      if (email.isBlank()) throw new IllegalArgumentException("Email is required");
      this.email = email.trim().toLowerCase(Locale.ROOT);
    }
    if (this.email == null) throw new IllegalArgumentException("Email is required");
    if (passwordHash != null) {
      if (passwordHash.isBlank()) throw new IllegalArgumentException("Password hash is required");
      this.passwordHash = passwordHash;
    }
    if (role != null) this.role = role;
  }
}
