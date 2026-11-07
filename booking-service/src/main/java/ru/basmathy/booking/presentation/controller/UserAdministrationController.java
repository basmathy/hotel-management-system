package ru.basmathy.booking.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.booking.application.service.UserAdministrationService;
import ru.basmathy.booking.presentation.dto.*;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdministrationController {
  private final UserAdministrationService users;

  @GetMapping
  public Page<UserResponse> list(@PageableDefault(size = 20, sort = "id") Pageable page) {
    return users.list(page);
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable Long id) {
    return users.get(id);
  }

  @PutMapping("/{id}")
  public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest r) {
    return users.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    users.delete(id);
  }
}
