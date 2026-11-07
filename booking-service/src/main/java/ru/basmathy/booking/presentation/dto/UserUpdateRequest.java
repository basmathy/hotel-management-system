package ru.basmathy.booking.presentation.dto;

import jakarta.validation.constraints.*;
import ru.basmathy.booking.domain.model.UserRole;

public record UserUpdateRequest(
    @Email @Size(min = 1, max = 255) String email,
    @Size(min = 8, max = 72) String password,
    UserRole role) {}
