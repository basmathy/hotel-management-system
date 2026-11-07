package ru.basmathy.hotel.presentation.dto;

import java.util.List;

public record HotelOccupancyStatisticsResponse(
    Long hotelId,
    long bookingCount,
    long occupiedNights,
    long capacityNights,
    Double occupancyRate,
    List<RoomOccupancyStatisticsResponse> rooms) {}
