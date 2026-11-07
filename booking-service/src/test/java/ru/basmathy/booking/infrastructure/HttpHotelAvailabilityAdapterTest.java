package ru.basmathy.booking.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import ru.basmathy.booking.application.port.*;
import ru.basmathy.booking.configuration.*;
import ru.basmathy.booking.domain.model.StayPeriod;
import ru.basmathy.booking.infrastructure.client.HttpHotelAvailabilityAdapter;

class HttpHotelAvailabilityAdapterTest {
  WireMockServer server;
  HttpHotelAvailabilityAdapter adapter;
  CircuitBreaker breaker;
  StayPeriod period = new StayPeriod(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));

  @BeforeEach
  void start() {
    server = new WireMockServer(0);
    server.start();
    var client =
        new HotelClientConfiguration()
            .hotelClient(
                RestClient.builder(),
                server.baseUrl(),
                "test-internal",
                Duration.ofMillis(500),
                Duration.ofMillis(500));
    breaker = new ResilienceConfiguration().hotelCircuitBreaker();
    adapter =
        new HttpHotelAvailabilityAdapter(
            client, new ResilienceConfiguration().hotelRetry(3), breaker);
  }

  @AfterEach
  void stop() {
    server.stop();
    MDC.clear();
  }

  @Test
  void retriesServerFailureAndPropagatesInternalTokenAndCorrelation() {
    server.stubFor(
        post(urlEqualTo("/internal/rooms/2/reservations"))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("ok"));
    server.stubFor(
        post(urlEqualTo("/internal/rooms/2/reservations"))
            .inScenario("retry")
            .whenScenarioStateIs("ok")
            .willReturn(okJson("{\"operationId\":\"op\",\"status\":\"HELD\"}")));
    MDC.put("correlationId", "test-request");
    adapter.reserve(2L, "op", period);
    server.verify(
        2,
        postRequestedFor(urlEqualTo("/internal/rooms/2/reservations"))
            .withHeader("X-Internal-Token", equalTo("test-internal"))
            .withHeader("X-Correlation-ID", equalTo("test-request")));
  }

  @Test
  void conflictsAreNotRetried() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(409)));
    assertThatThrownBy(() -> adapter.reserve(2L, "op", period))
        .isInstanceOf(RoomAllocationConflict.class);
    server.verify(1, postRequestedFor(anyUrl()));
  }

  @Test
  void exhaustsRetryBudget() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(503)));
    assertThatThrownBy(() -> adapter.reserve(2L, "op", period))
        .isInstanceOf(HotelUnavailableException.class);
    server.verify(3, postRequestedFor(anyUrl()));
  }

  @Test
  void enforcesReadTimeout() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200).withFixedDelay(1500)));
    assertThatThrownBy(() -> adapter.reserve(2L, "op", period))
        .isInstanceOf(HotelUnavailableException.class);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
  }

  @Test
  void openCircuitDoesNotCallHttp() {
    breaker.transitionToOpenState();
    assertThatThrownBy(() -> adapter.release(2L, "op", period))
        .isInstanceOf(HotelUnavailableException.class);
    server.verify(0, postRequestedFor(anyUrl()));
  }

  @Test
  void missingAcknowledgementIsNotTreatedAsSuccessfulAllocation() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)));
    assertThatThrownBy(() -> adapter.reserve(2L, "op", period))
        .isInstanceOf(HotelUnavailableException.class);
    server.verify(3, postRequestedFor(anyUrl()));
  }

  @Test
  void releaseOfDeletedRoomIsAlreadyComplete() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(404)));
    assertThatCode(() -> adapter.release(2L, "op", period)).doesNotThrowAnyException();
    server.verify(1, postRequestedFor(anyUrl()));
  }
}
