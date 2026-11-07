package ru.basmathy.booking.presentation.dto;

import jakarta.validation.constraints.*;

public record UserAuthenticationRequest(
    @NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 72) String password) {}
