package ru.basmathy.booking.presentation.dto;

import jakarta.validation.constraints.*;

public record UserRegistrationRequest(
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,80}") String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password) {}
