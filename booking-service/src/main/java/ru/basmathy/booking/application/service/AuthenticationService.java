package ru.basmathy.booking.application.service;

import ru.basmathy.booking.presentation.dto.*;

public interface AuthenticationService {
  UserResponse register(UserRegistrationRequest request);

  AuthenticationResponse authenticate(UserAuthenticationRequest request);
}
