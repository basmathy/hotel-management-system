package ru.basmathy.booking.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.infrastructure.persistence.*;
import ru.basmathy.booking.presentation.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultUserAdministrationService implements UserAdministrationService {
  private final UserRepository users;
  private final BookingRepository bookings;
  private final PasswordEncoder passwords;

  public Page<UserResponse> list(Pageable p) {
    return users.findAll(p).map(this::response);
  }

  public UserResponse get(Long id) {
    return response(users.findById(id).orElseThrow(() -> new NotFoundException("User not found")));
  }

  @Transactional
  public UserResponse update(Long id, UserUpdateRequest r) {
    User u = locked(id);
    u.update(r.email(), r.password() == null ? null : passwords.encode(r.password()), r.role());
    return response(u);
  }

  @Transactional
  public void delete(Long id) {
    User u = locked(id);
    if (bookings.existsByOwnerId(id)) throw new DomainConflict("User has booking history");
    users.delete(u);
  }

  private User locked(Long id) {
    return users.findLockedById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private UserResponse response(User u) {
    return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole());
  }
}
