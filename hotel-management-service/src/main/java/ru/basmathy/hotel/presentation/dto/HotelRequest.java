package ru.basmathy.hotel.presentation.dto;

import jakarta.validation.constraints.*;

public record HotelRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String city,
    @NotBlank @Size(max = 255) String address) {}
