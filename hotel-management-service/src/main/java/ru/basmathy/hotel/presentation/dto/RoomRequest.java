package ru.basmathy.hotel.presentation.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RoomRequest(
    @NotNull @Positive Long hotelId,
    @NotBlank @Size(max = 255) String number,
    @Min(1) int capacity,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
    boolean operational) {}
