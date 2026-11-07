package ru.basmathy.booking.integration;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import ru.basmathy.booking.BookingApplication;
import ru.basmathy.booking.application.service.*;
import ru.basmathy.booking.domain.model.BookingStatus;
import ru.basmathy.booking.infrastructure.persistence.*;
import ru.basmathy.booking.presentation.dto.*;
import ru.basmathy.hotel.HotelManagementApplication;
import ru.basmathy.hotel.application.service.HotelManagementService;
import ru.basmathy.hotel.application.service.RoomAvailabilityService;
import ru.basmathy.hotel.application.service.RoomManagementService;
import ru.basmathy.hotel.domain.model.ReservationStatus;
import ru.basmathy.hotel.infrastructure.persistence.HotelRepository;
import ru.basmathy.hotel.infrastructure.persistence.RoomRepository;
import ru.basmathy.hotel.infrastructure.persistence.RoomReservationRepository;
import ru.basmathy.hotel.presentation.dto.HotelRequest;
import ru.basmathy.hotel.presentation.dto.RoomRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedBookingTest {
  ConfigurableApplicationContext hotelContext, bookingContext;
  HotelProxy proxy;
  RestClient client;
  BookingRepository bookings;
  UserRepository users;
  CompensationTaskRepository tasks;
  BookingTransactionService transactions;
  BookingCompensationService compensation;
  HotelManagementService hotelService;
  RoomManagementService roomService;
  RoomRepository rooms;
  RoomReservationRepository reservations;
  Long hotelId, roomId;
  String token;
  Long owner;
  LocalDate start = LocalDate.now().plusDays(10);

  record Outcome(int status, BookingResponse booking) {}

  @BeforeAll
  void startServices() throws Exception {
    hotelContext =
        new SpringApplicationBuilder(HotelManagementApplication.class)
            .profiles("test")
            .run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:hotel-http;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--eureka.client.enabled=false",
                "--security.jwt.secret=test-signing-secret-with-at-least-32-bytes-long",
                "--security.jwt.issuer=hotel-platform",
                "--security.jwt.audience=hotel-api",
                "--security.internal-token=test-internal",
                "--logging.level.root=WARN",
                "--debug=false");
    int hotelPort = ((WebServerApplicationContext) hotelContext).getWebServer().getPort();
    proxy = new HotelProxy("http://localhost:" + hotelPort);
    bookingContext =
        new SpringApplicationBuilder(BookingApplication.class)
            .profiles("test")
            .run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:booking-http;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--eureka.client.enabled=false",
                "--hotel.base-url=" + proxy.baseUrl(),
                "--hotel.internal-token=test-internal",
                "--hotel.read-timeout=PT10S",
                "--hotel.retry-attempts=1",
                "--saga.scheduling-enabled=false",
                "--security.jwt.secret=test-signing-secret-with-at-least-32-bytes-long",
                "--security.jwt.issuer=hotel-platform",
                "--security.jwt.audience=hotel-api",
                "--logging.level.root=WARN",
                "--debug=false");
    client =
        RestClient.builder()
            .baseUrl(
                "http://localhost:"
                    + ((WebServerApplicationContext) bookingContext).getWebServer().getPort())
            .requestFactory(
                new org.springframework.http.client.JdkClientHttpRequestFactory(
                    HttpClient.newHttpClient()))
            .build();
    bookings = bookingContext.getBean(BookingRepository.class);
    users = bookingContext.getBean(UserRepository.class);
    tasks = bookingContext.getBean(CompensationTaskRepository.class);
    transactions = bookingContext.getBean(BookingTransactionService.class);
    compensation = bookingContext.getBean(BookingCompensationService.class);
    hotelService = hotelContext.getBean(HotelManagementService.class);
    roomService = hotelContext.getBean(RoomManagementService.class);
    rooms = hotelContext.getBean(RoomRepository.class);
    reservations = hotelContext.getBean(RoomReservationRepository.class);
  }

  @AfterAll
  void stopServices() {
    if (bookingContext != null) bookingContext.close();
    if (proxy != null) proxy.close();
    if (hotelContext != null) hotelContext.close();
  }

  @BeforeEach
  void fixture() {
    proxy.reset();
    bookingContext.getBean(io.github.resilience4j.circuitbreaker.CircuitBreaker.class).reset();
    tasks.deleteAll();
    bookings.deleteAll();
    users.deleteAll();
    reservations.deleteAll();
    rooms.deleteAll();
    hotelContext.getBean(HotelRepository.class).deleteAll();
    hotelId = hotelService.create(new HotelRequest("HTTP Hotel", "City", "Address")).id();
    roomId = roomService.create(new RoomRequest(hotelId, "101", 2, BigDecimal.TEN, true)).id();
    owner =
        bookingContext
            .getBean(AuthenticationService.class)
            .register(new UserRegistrationRequest("httpuser", "http@example.test", "Password123!"))
            .id();
    token =
        client
            .post()
            .uri("/api/auth/login")
            .body(new UserAuthenticationRequest("httpuser", "Password123!"))
            .retrieve()
            .body(AuthenticationResponse.class)
            .accessToken();
  }

  BookingRequest request(UUID key, Long selected) {
    return new BookingRequest(key, hotelId, selected, start, start.plusDays(3));
  }

  Outcome create(BookingRequest request) {
    return client
        .post()
        .uri("/api/bookings")
        .header("Authorization", "Bearer " + token)
        .header("X-Correlation-ID", "distributed-test")
        .body(request)
        .exchange(
            (r, s) ->
                new Outcome(
                    s.getStatusCode().value(),
                    s.getStatusCode().is2xxSuccessful() ? s.bodyTo(BookingResponse.class) : null));
  }

  @Test
  void realHttpSuccessAndCancellationUpdateBothDatabases() {
    Outcome result = create(request(UUID.randomUUID(), roomId));
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    assertHeld(1);
    assertThat(proxy.correlation.get()).isEqualTo("distributed-test");
    var cancelled =
        client
            .post()
            .uri("/api/bookings/" + result.booking().id() + "/cancel")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(BookingResponse.class);
    assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
    assertHeld(0);
    assertThat(tasks.findAll()).allMatch(t -> t.isCompleted());
  }

  @Test
  void twoConcurrentBookingsOnlyOneIsConfirmed() throws Exception {
    var outcomes =
        race(
            () -> create(request(UUID.randomUUID(), roomId)),
            () -> create(request(UUID.randomUUID(), roomId)));
    assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(201, 409);
    assertThat(bookings.findAll())
        .filteredOn(b -> b.getStatus() == BookingStatus.CONFIRMED)
        .hasSize(1);
    assertHeld(1);
  }

  @Test
  void concurrentSameRequestCreatesExactlyOneBooking() throws Exception {
    BookingRequest request = request(UUID.randomUUID(), roomId);
    var outcomes = race(() -> create(request), () -> create(request));
    assertThat(outcomes).allMatch(o -> o.status() == 201 || o.status() == 202);
    assertThat(outcomes)
        .extracting(o -> o.booking().id())
        .containsOnly(outcomes.getFirst().booking().id());
    assertThat(bookings.count()).isEqualTo(1);
    assertHeld(1);
  }

  @Test
  void changedReplayParametersConflictWithoutChangingHotel() {
    UUID key = UUID.randomUUID();
    assertThat(create(request(key, roomId)).status()).isEqualTo(201);
    assertThat(create(new BookingRequest(key, hotelId, roomId, start, start.plusDays(4))).status())
        .isEqualTo(409);
    assertThat(bookings.count()).isEqualTo(1);
    assertHeld(1);
  }

  @Test
  void lostAllocationResponseIsCompensated() {
    proxy.dropReserveResponses = true;
    var outcome = create(request(UUID.randomUUID(), roomId));
    assertThat(outcome.status())
        .withFailMessage("Outcome=%s, events=%s", outcome, proxy.events)
        .isEqualTo(503);
    assertThat(bookings.findAll())
        .singleElement()
        .satisfies(b -> assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED));
    assertHeld(0);
    assertThat(reservations.findAll())
        .singleElement()
        .satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED));
    assertThat(tasks.findAll()).allMatch(t -> t.isCompleted());
  }

  @Test
  void unavailableCompensationIsDurableAndSchedulerCompletesAfterRecovery() {
    proxy.dropReserveResponses = true;
    proxy.failRelease = true;
    var outcome = create(request(UUID.randomUUID(), roomId));
    assertThat(outcome.status())
        .withFailMessage("Outcome=%s, events=%s", outcome, proxy.events)
        .isEqualTo(503);
    assertHeld(1);
    var task = tasks.findAll().getFirst();
    assertThat(task.isCompleted()).isFalse();
    assertThat(task.getAttempts()).isEqualTo(1);
    proxy.failRelease = false;
    ReflectionTestUtils.setField(task, "nextAttemptAt", Instant.EPOCH);
    tasks.saveAndFlush(task);
    new CompensationRetryScheduler(tasks, compensation).run();
    assertThat(tasks.findById(task.getOperationId()).orElseThrow().isCompleted()).isTrue();
    assertHeld(0);
  }

  @Test
  void recoveryWinsAgainstSuccessfulResponseAndCannotBeReconfirmed() throws Exception {
    proxy.pauseAfter = true;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Outcome> running = executor.submit(() -> create(request(UUID.randomUUID(), roomId)));
    try {
      proxy.awaitPaused();
      assertHeld(1);
      var booking = bookings.findAll().getFirst();
      transactions.recover(booking.getId(), Instant.now().plusSeconds(1));
      compensation.compensate(booking.getId() + "-" + roomId);
      assertHeld(0);
      proxy.resume.countDown();
      assertThat(running.get(10, TimeUnit.SECONDS).booking().status())
          .isEqualTo(BookingStatus.CANCELLED);
      assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
          .isEqualTo(BookingStatus.CANCELLED);
      assertHeld(0);
    } finally {
      proxy.resume.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void releaseBeforeDelayedAllocationPreventsSlotFromBeingHeld() throws Exception {
    proxy.pauseBefore = true;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Outcome> running = executor.submit(() -> create(request(UUID.randomUUID(), roomId)));
    try {
      proxy.awaitPaused();
      var booking = bookings.findAll().getFirst();
      transactions.recover(booking.getId(), Instant.now().plusSeconds(1));
      compensation.compensate(booking.getId() + "-" + roomId);
      proxy.resume.countDown();
      assertThat(running.get(10, TimeUnit.SECONDS).status()).isEqualTo(409);
      assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
          .isEqualTo(BookingStatus.CANCELLED);
      assertHeld(0);
      assertThat(reservations.findAll())
          .singleElement()
          .satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED));
    } finally {
      proxy.resume.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void successfulConfirmationWinsAgainstLaterRecovery() {
    var result = create(request(UUID.randomUUID(), roomId));
    transactions.recover(result.booking().id(), Instant.now().plusSeconds(1));
    assertThat(bookings.findById(result.booking().id()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.CONFIRMED);
    assertThat(tasks.count()).isZero();
    assertHeld(1);
  }

  @Test
  void autoSelectionTriesNextCandidateAfterRealAllocationRace() {
    Long second = roomService.create(new RoomRequest(hotelId, "102", 2, BigDecimal.TEN, true)).id();
    AtomicBoolean first = new AtomicBoolean(true);
    proxy.onReserve =
        id -> {
          if (first.getAndSet(false))
            hotelContext
                .getBean(RoomAvailabilityService.class)
                .reserve(
                    id,
                    "competitor",
                    new ru.basmathy.hotel.domain.model.StayPeriod(start, start.plusDays(3)));
        };
    var result = create(request(UUID.randomUUID(), null));
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.booking().roomId()).isEqualTo(second);
    assertThat(reservations.findAll()).hasSize(2);
    assertThat(rooms.findById(second).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void adjacentHttpBookingsSucceedWhileOverlappingBookingFails() {
    assertThat(create(request(UUID.randomUUID(), roomId)).status()).isEqualTo(201);
    assertThat(
            create(
                    new BookingRequest(
                        UUID.randomUUID(), hotelId, roomId, start.plusDays(3), start.plusDays(5)))
                .status())
        .isEqualTo(201);
    assertThat(
            create(
                    new BookingRequest(
                        UUID.randomUUID(), hotelId, roomId, start.plusDays(2), start.plusDays(4)))
                .status())
        .isEqualTo(409);
    assertHeld(2);
  }

  void assertHeld(long count) {
    assertThat(reservations.findAll())
        .filteredOn(r -> r.getStatus() == ReservationStatus.HELD)
        .hasSize((int) count);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(count);
  }

  <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      Future<T> a =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return first.call();
              });
      Future<T> b =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return second.call();
              });
      return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  static class HotelProxy implements AutoCloseable {
    final HttpServer server;
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final HttpClient http = HttpClient.newHttpClient();
    final String target;
    final List<String> events = new CopyOnWriteArrayList<>();
    volatile boolean dropReserveResponses, failRelease, pauseBefore, pauseAfter;
    volatile CountDownLatch paused = new CountDownLatch(1), resume = new CountDownLatch(1);
    volatile Consumer<Long> onReserve = id -> {};
    final AtomicReference<String> correlation = new AtomicReference<>();

    HotelProxy(String target) throws IOException {
      this.target = target;
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.setExecutor(executor);
      server.createContext("/", this::handle);
      server.start();
    }

    String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    void reset() {
      events.clear();
      dropReserveResponses = false;
      failRelease = false;
      pauseBefore = false;
      pauseAfter = false;
      paused = new CountDownLatch(1);
      resume = new CountDownLatch(1);
      onReserve = id -> {};
      correlation.set(null);
    }

    void awaitPaused() throws InterruptedException {
      assertThat(paused.await(8, TimeUnit.SECONDS)).isTrue();
    }

    void waitAtGate() throws InterruptedException {
      paused.countDown();
      if (!resume.await(12, TimeUnit.SECONDS)) throw new AssertionError("Proxy gate timed out");
    }

    void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      boolean reserve =
          path.endsWith("/reservations") && exchange.getRequestMethod().equals("POST");
      try {
        correlation.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
        if (failRelease && path.endsWith("/release")) {
          exchange.sendResponseHeaders(503, -1);
          return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (reserve) {
          if (pauseBefore) waitAtGate();
          onReserve.accept(Long.valueOf(path.split("/")[3]));
        }
        var builder =
            HttpRequest.newBuilder(URI.create(target + exchange.getRequestURI()))
                .method(
                    exchange.getRequestMethod(),
                    body.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        for (String header : List.of("Content-Type", "X-Internal-Token", "X-Correlation-ID")) {
          String value = exchange.getRequestHeaders().getFirst(header);
          if (value != null) builder.header(header, value);
        }
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (reserve && pauseAfter) waitAtGate();
        if (reserve && dropReserveResponses) {
          events.add("Dropped Hotel status=" + response.statusCode());
          exchange.sendResponseHeaders(504, -1);
          return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.statusCode(), response.body().length);
        exchange.getResponseBody().write(response.body());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    }

    public void close() {
      resume.countDown();
      server.stop(0);
      executor.shutdownNow();
    }
  }
}
