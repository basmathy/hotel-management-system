package ru.basmathy.hotel.presentation.dto;

import java.math.BigDecimal;

public record RoomResponse(
    Long id,
    Long hotelId,
    String number,
    int capacity,
    BigDecimal price,
    boolean operational,
    long timesBooked) {}
