package ru.basmathy.booking.configuration;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import ru.basmathy.booking.application.port.HotelUnavailableException;

@Configuration
public class ResilienceConfiguration {
  @Bean
  public Retry hotelRetry(@Value("${hotel.retry-attempts:3}") int attempts) {
    return Retry.of(
        "hotel",
        RetryConfig.custom()
            .maxAttempts(attempts)
            .waitDuration(Duration.ofMillis(100))
            .retryOnException(e -> e instanceof HotelUnavailableException)
            .build());
  }

  @Bean
  public CircuitBreaker hotelCircuitBreaker() {
    return CircuitBreaker.of(
        "hotel",
        CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .recordException(e -> e instanceof HotelUnavailableException)
            .ignoreExceptions(ru.basmathy.booking.domain.model.DomainConflict.class)
            .build());
  }
}
