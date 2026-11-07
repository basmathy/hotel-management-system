package ru.basmathy.booking.application.port;

public class HotelUnavailableException extends RuntimeException {
  public HotelUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
