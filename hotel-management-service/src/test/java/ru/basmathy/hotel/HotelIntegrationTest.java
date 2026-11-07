package ru.basmathy.hotel;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.basmathy.hotel.application.service.*;
import ru.basmathy.hotel.domain.model.*;
import ru.basmathy.hotel.infrastructure.persistence.*;
import ru.basmathy.hotel.presentation.dto.*;

@SpringBootTest(properties = {"debug=false", "logging.level.root=WARN"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HotelIntegrationTest {
  @Autowired RoomAvailabilityService availability;
  @Autowired RoomManagementService roomService;
  @Autowired HotelManagementService hotelService;
  @Autowired RoomRecommendationService recommendations;
  @Autowired RoomOccupancyStatisticsService statistics;
  @Autowired RoomRepository rooms;
  @Autowired HotelRepository hotels;
  @Autowired RoomReservationRepository reservations;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager manager;
  Long hotelId, roomId;
  LocalDate start = LocalDate.of(2030, 1, 1);
  StayPeriod period = new StayPeriod(start, start.plusDays(4));

  @BeforeEach
  void fixture() {
    reservations.deleteAll();
    rooms.deleteAll();
    hotels.deleteAll();
    hotelId = hotelService.create(new HotelRequest("Hotel", "City", "Address")).id();
    roomId = roomService.create(new RoomRequest(hotelId, "101", 2, BigDecimal.TEN, true)).id();
  }

  @Test
  void exactlyOneConcurrentAllocationWins() throws Exception {
    var result = race(() -> reserve("first"), () -> reserve("second"));
    assertThat(result).containsExactlyInAnyOrder(true, false);
    assertThat(reservations.count()).isEqualTo(1);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void concurrentDuplicateOperationAllocatesOnce() throws Exception {
    assertThat(race(() -> reserve("same"), () -> reserve("same"))).containsExactly(true, true);
    assertThat(reservations.count()).isEqualTo(1);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void earlyReleasePreventsLateAllocationAndRepeatedReleaseDoesNotDecrement() {
    availability.release(roomId, "late", period);
    assertThatThrownBy(() -> availability.reserve(roomId, "late", period))
        .isInstanceOf(DomainConflict.class);
    availability.release(roomId, "late", period);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
  }

  @Test
  void normalReleaseIsIdempotentAndLeavesTombstone() {
    availability.reserve(roomId, "a", period);
    availability.release(roomId, "a", period);
    availability.release(roomId, "a", period);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
    assertThat(reservations.findById("a").orElseThrow().getStatus())
        .isEqualTo(ReservationStatus.RELEASED);
  }

  @Test
  void changedOperationParametersConflict() {
    availability.reserve(roomId, "a", period);
    assertThatThrownBy(
            () -> availability.reserve(roomId, "a", new StayPeriod(start, start.plusDays(5))))
        .isInstanceOf(DomainConflict.class);
    assertThatThrownBy(
            () -> availability.release(roomId, "a", new StayPeriod(start, start.plusDays(5))))
        .isInstanceOf(DomainConflict.class);
  }

  @Test
  void adjacentIntervalsAreAllowedAndOverlapsRejected() {
    availability.reserve(roomId, "a", period);
    availability.reserve(roomId, "b", new StayPeriod(start.plusDays(4), start.plusDays(5)));
    assertThatThrownBy(
            () ->
                availability.reserve(
                    roomId, "c", new StayPeriod(start.plusDays(3), start.plusDays(6))))
        .isInstanceOf(DomainConflict.class);
  }

  @Test
  void recommendationsExcludeOccupiedAndDisabledRooms() {
    Long free = roomService.create(new RoomRequest(hotelId, "102", 2, BigDecimal.TEN, true)).id();
    Long disabled =
        roomService.create(new RoomRequest(hotelId, "103", 2, BigDecimal.TEN, false)).id();
    availability.reserve(roomId, "a", period);
    assertThat(recommendations.recommend(hotelId, 1, period))
        .extracting(RoomRecommendationResponse::roomId)
        .containsExactly(free);
    assertThatThrownBy(() -> availability.reserve(disabled, "b", period))
        .isInstanceOf(DomainConflict.class);
  }

  @Test
  void unsafeDeletesAreRejected() {
    assertThatThrownBy(() -> hotelService.delete(hotelId)).isInstanceOf(DomainConflict.class);
    availability.release(roomId, "tombstone", period);
    assertThatThrownBy(() -> roomService.delete(roomId)).isInstanceOf(DomainConflict.class);
  }

  @Test
  void uniqueRoomNumbersAreEnforcedByDatabase() {
    assertThatThrownBy(
            () -> roomService.create(new RoomRequest(hotelId, "101", 1, BigDecimal.ONE, true)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void reservationKeyIsGloballyUniqueAndFailedInsertRollsBackCounter() {
    Long other = roomService.create(new RoomRequest(hotelId, "102", 1, BigDecimal.ONE, true)).id();
    availability.reserve(roomId, "global", period);
    assertThatThrownBy(() -> availability.reserve(other, "global", period))
        .isInstanceOf(DomainConflict.class);
    assertThat(rooms.findById(other).orElseThrow().getTimesBooked()).isZero();
  }

  @Test
  void statisticsClipNightsAndExcludeDisabledCapacity() {
    availability.reserve(roomId, "a", period);
    Long disabled =
        roomService.create(new RoomRequest(hotelId, "102", 1, BigDecimal.ONE, false)).id();
    var stats = statistics.hotel(hotelId, new StayPeriod(start.plusDays(2), start.plusDays(7)));
    assertThat(stats.occupiedNights()).isEqualTo(2);
    assertThat(stats.capacityNights()).isEqualTo(5);
    assertThat(stats.occupancyRate()).isEqualTo(0.4);
    assertThat(statistics.room(disabled, period).occupancyRate()).isNull();
  }

  @Test
  void lockWaitIsBoundedAndMappedToConflict() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
    Future<?> holder =
        executor.submit(
            () ->
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        tx -> {
                          rooms.findLockedById(roomId).orElseThrow();
                          locked.countDown();
                          try {
                            if (!release.await(15, TimeUnit.SECONDS))
                              throw new AssertionError("Release not signalled");
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                          }
                        }));
    try {
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      mvc.perform(
              post("/internal/rooms/" + roomId + "/reservations")
                  .header("X-Internal-Token", "test-internal")
                  .contentType("application/json")
                  .content(command("blocked")))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CONCURRENT_CONFLICT"));
    } finally {
      release.countDown();
      holder.get(5, TimeUnit.SECONDS);
      executor.shutdownNow();
    }
  }

  @Test
  void mvcSecurityValidationAndInternalBoundary() throws Exception {
    mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json")
                .content("{\"name\":\"H\",\"city\":\"C\",\"address\":\"A\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/internal/rooms/" + roomId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/internal/rooms/" + roomId).header("X-Internal-Token", "test-internal"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .header("X-Correlation-ID", "request-123"))
        .andExpect(header().string("X-Correlation-ID", "request-123"));
  }

  @Test
  void adminCrudAndFilters() throws Exception {
    mvc.perform(
            post("/api/hotels")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"name\":\"Another\",\"city\":\"Moscow\",\"address\":\"Street\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            get("/api/hotels")
                .param("city", "Moscow")
                .param("size", "1")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  private String command(String op) {
    return "{\"operationId\":\""
        + op
        + "\",\"checkIn\":\""
        + period.getCheckIn()
        + "\",\"checkOut\":\""
        + period.getCheckOut()
        + "\"}";
  }

  private boolean reserve(String op) {
    try {
      availability.reserve(roomId, op, period);
      return true;
    } catch (DomainConflict e) {
      return false;
    }
  }

  private List<Boolean> race(Callable<Boolean> a, Callable<Boolean> b) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CyclicBarrier gate = new CyclicBarrier(2);
    try {
      Future<Boolean> first =
          executor.submit(
              () -> {
                gate.await(5, TimeUnit.SECONDS);
                return a.call();
              });
      Future<Boolean> second =
          executor.submit(
              () -> {
                gate.await(5, TimeUnit.SECONDS);
                return b.call();
              });
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }
}
