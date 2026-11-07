package ru.basmathy.booking.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.booking.application.service.AuthenticationService;
import ru.basmathy.booking.presentation.dto.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
  private final AuthenticationService authentication;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody UserRegistrationRequest r) {
    return authentication.register(r);
  }

  @PostMapping("/login")
  public AuthenticationResponse login(@Valid @RequestBody UserAuthenticationRequest r) {
    return authentication.authenticate(r);
  }
}
