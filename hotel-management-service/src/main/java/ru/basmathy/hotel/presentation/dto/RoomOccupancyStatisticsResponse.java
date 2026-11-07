package ru.basmathy.hotel.presentation.dto;

public record RoomOccupancyStatisticsResponse(
    Long roomId,
    boolean operational,
    long bookingCount,
    long occupiedNights,
    long capacityNights,
    Double occupancyRate) {}
