package ru.basmathy.hotel.presentation.dto;

import java.math.BigDecimal;

public record RoomRecommendationResponse(
    Long roomId, Long hotelId, String number, long timesBooked, BigDecimal price) {}
