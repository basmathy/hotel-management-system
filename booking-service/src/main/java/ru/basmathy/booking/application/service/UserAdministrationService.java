package ru.basmathy.booking.application.service;

import org.springframework.data.domain.*;
import ru.basmathy.booking.presentation.dto.*;

public interface UserAdministrationService {
  Page<UserResponse> list(Pageable page);

  UserResponse get(Long id);

  UserResponse update(Long id, UserUpdateRequest request);

  void delete(Long id);
}
