package ru.basmathy.booking.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.booking.configuration.JwtTokenProvider;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.infrastructure.persistence.UserRepository;
import ru.basmathy.booking.presentation.dto.*;

@Service
@RequiredArgsConstructor
public class JwtAuthenticationService implements AuthenticationService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtTokenProvider tokens;

  @Transactional
  public UserResponse register(UserRegistrationRequest r) {
    User user =
        users.saveAndFlush(
            new User(r.username(), r.email(), passwords.encode(r.password()), UserRole.USER));
    return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
  }

  @Transactional(readOnly = true)
  public AuthenticationResponse authenticate(UserAuthenticationRequest r) {
    User user =
        users
            .findByUsername(r.username().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    if (!passwords.matches(r.password(), user.getPasswordHash()))
      throw new BadCredentialsException("Invalid credentials");
    return new AuthenticationResponse(tokens.issue(user), "Bearer", 3600);
  }
}
