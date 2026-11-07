package ru.basmathy.booking.presentation.dto;

public record AuthenticationResponse(String accessToken, String tokenType, long expiresIn) {}
