package ru.basmathy.booking.infrastructure.client;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ru.basmathy.booking.application.port.*;
import ru.basmathy.booking.domain.model.*;

@Component
@RequiredArgsConstructor
public class HttpHotelAvailabilityAdapter implements HotelAvailabilityPort {
  private final RestClient hotelClient;
  private final Retry hotelRetry;
  private final CircuitBreaker hotelCircuitBreaker;

  private record ReservationCommand(String operationId, LocalDate checkIn, LocalDate checkOut) {}

  private record ReservationAcknowledgement(String operationId, String status) {}

  public List<Candidate> candidates(Long hotelId, StayPeriod period) {
    return invoke(
        () ->
            Objects.requireNonNull(
                hotelClient
                    .get()
                    .uri(
                        b ->
                            b.path("/internal/rooms/available")
                                .queryParam("hotelId", hotelId)
                                .queryParam("checkIn", period.getCheckIn())
                                .queryParam("checkOut", period.getCheckOut())
                                .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Candidate>>() {})));
  }

  public HotelRoom room(Long id) {
    return invoke(
        () ->
            Objects.requireNonNull(
                hotelClient
                    .get()
                    .uri("/internal/rooms/{id}", id)
                    .retrieve()
                    .body(HotelRoom.class)));
  }

  public void reserve(Long id, String operation, StayPeriod period) {
    command(id, operation, period, "", "HELD");
  }

  public void release(Long id, String operation, StayPeriod period) {
    command(id, operation, period, "/release", "RELEASED");
  }

  private void command(
      Long id, String operation, StayPeriod period, String suffix, String expectedStatus) {
    invoke(
        () -> {
          try {
            var ack =
                hotelClient
                    .post()
                    .uri("/internal/rooms/{id}/reservations" + suffix, id)
                    .body(
                        new ReservationCommand(
                            operation, period.getCheckIn(), period.getCheckOut()))
                    .retrieve()
                    .body(ReservationAcknowledgement.class);
            if (ack == null
                || !operation.equals(ack.operationId())
                || !expectedStatus.equals(ack.status()))
              throw new HotelUnavailableException(
                  "Missing or inconsistent Hotel acknowledgement", null);
          } catch (HttpClientErrorException.NotFound e) {
            // A deleted room cannot retain or subsequently accept this reservation.
            if (!suffix.equals("/release")) throw e;
          }
          return null;
        });
  }

  private <T> T invoke(Supplier<T> call) {
    Supplier<T> attempt =
        () -> {
          try {
            return call.get();
          } catch (HttpClientErrorException.Conflict e) {
            throw new RoomAllocationConflict("Room operation conflicts with current state");
          } catch (HttpClientErrorException e) {
            throw new DomainConflict("Hotel rejected request: " + e.getStatusCode().value());
          } catch (RestClientException | NullPointerException e) {
            throw new HotelUnavailableException("Hotel request failed", e);
          }
        };
    try {
      return Retry.decorateSupplier(
              hotelRetry, CircuitBreaker.decorateSupplier(hotelCircuitBreaker, attempt))
          .get();
    } catch (CallNotPermittedException e) {
      throw new HotelUnavailableException("Hotel circuit breaker is open", e);
    }
  }
}
