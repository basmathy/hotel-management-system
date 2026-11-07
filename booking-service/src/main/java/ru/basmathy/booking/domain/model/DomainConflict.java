package ru.basmathy.booking.domain.model;

public class DomainConflict extends RuntimeException {
  public DomainConflict(String message) {
    super(message);
  }
}
