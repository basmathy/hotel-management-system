package ru.basmathy.hotel.domain.model;

public class DomainConflict extends RuntimeException {
  public DomainConflict(String message) {
    super(message);
  }
}
