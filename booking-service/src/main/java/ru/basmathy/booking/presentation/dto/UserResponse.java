package ru.basmathy.booking.presentation.dto;

import ru.basmathy.booking.domain.model.UserRole;

public record UserResponse(Long id, String username, String email, UserRole role) {}
