package ru.basmathy.booking.presentation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.basmathy.booking.application.port.*;
import ru.basmathy.booking.application.service.*;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.infrastructure.persistence.*;
import ru.basmathy.booking.presentation.dto.*;

@SpringBootTest(properties = {"debug=false", "logging.level.root=WARN"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BookingMvcTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired BookingRepository bookings;
  @Autowired CompensationTaskRepository tasks;
  @Autowired PasswordEncoder passwords;
  @Autowired BookingApplicationService app;
  @MockitoBean HotelAvailabilityPort hotel;
  Long owner;

  @BeforeEach
  void fixture() {
    tasks.deleteAll();
    bookings.deleteAll();
    users.deleteAll();
    owner =
        users
            .saveAndFlush(
                new User(
                    "tester",
                    "tester@example.test",
                    passwords.encode("Password123!"),
                    UserRole.USER))
            .getId();
    when(hotel.room(2L)).thenReturn(new HotelAvailabilityPort.HotelRoom(2L, 1L, true));
  }

  BookingRequest request(UUID key) {
    return new BookingRequest(
        key, 1L, 2L, LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));
  }

  @Test
  void registrationCannotAssignAdminAndPasswordsAreHashed() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"username\":\"newuser\",\"email\":\"new@example.test\",\"password\":\"Password123!\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("USER"));
    var user = users.findByUsername("newuser").orElseThrow();
    assertThat(user.getPasswordHash()).isNotEqualTo("Password123!");
    assertThat(passwords.matches("Password123!", user.getPasswordHash())).isTrue();
  }

  @Test
  void realJwtHasOneHourLifetimeAndIsValidated() throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content("{\"username\":\"tester\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(body).get("accessToken").asText();
    var parsed = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet();
    assertThat(
            Duration.between(
                parsed.getIssueTime().toInstant(), parsed.getExpirationTime().toInstant()))
        .isEqualTo(Duration.ofHours(1));
    mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/bookings")
                .header(
                    "Authorization", "Bearer " + token.substring(0, token.length() - 5) + "wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void badCredentialsAndInvalidRegistration() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"tester\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticationAndAdminRoleAreEnforced() throws Exception {
    mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void ownerPolicyPreventsReadingAndCancellingAnotherBooking() throws Exception {
    BookingResponse b = app.create(owner, request(UUID.randomUUID()));
    var outsider =
        jwt().jwt(j -> j.subject("999")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    mvc.perform(get("/api/bookings/" + b.id()).with(outsider)).andExpect(status().isForbidden());
    mvc.perform(post("/api/bookings/" + b.id() + "/cancel").with(outsider))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/bookings/" + b.id())
                .with(
                    jwt()
                        .jwt(j -> j.subject(owner.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isOk());
  }

  @Test
  void replayIsStableAndChangedParametersReturn409() throws Exception {
    UUID key = UUID.randomUUID();
    BookingRequest r = request(key);
    var principal =
        jwt()
            .jwt(j -> j.subject(owner.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    String first =
        mvc.perform(
                post("/api/bookings")
                    .with(principal)
                    .contentType("application/json")
                    .content(json.writeValueAsString(r)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            post("/api/bookings")
                .with(principal)
                .contentType("application/json")
                .content(json.writeValueAsString(r)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(json.readTree(first).get("id").asText()));
    var changed = new BookingRequest(key, 1L, 3L, r.checkIn(), r.checkOut());
    mvc.perform(
            post("/api/bookings")
                .with(principal)
                .contentType("application/json")
                .content(json.writeValueAsString(changed)))
        .andExpect(status().isConflict());
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).reserve(anyLong(), anyString(), any());
  }

  @Test
  void cancellationPersistsTaskAtomicallyEvenWhenHotelIsDown() {
    var b = app.create(owner, request(UUID.randomUUID()));
    doThrow(new HotelUnavailableException("down", null))
        .when(hotel)
        .release(anyLong(), anyString(), any());
    assertThat(app.cancel(b.id(), owner, false).status()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(bookings.findById(b.id()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.CANCELLED);
    assertThat(tasks.findById(b.id() + "-2").orElseThrow().isCompleted()).isFalse();
  }

  @Test
  void adminCanUpdateRolesAndCannotDeleteUserWithHistory() throws Exception {
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(
            put("/api/users/" + owner)
                .with(admin)
                .contentType("application/json")
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    app.create(owner, request(UUID.randomUUID()));
    mvc.perform(delete("/api/users/" + owner).with(admin)).andExpect(status().isConflict());
  }

  @Test
  void invalidDatesReturn400() throws Exception {
    var r = request(UUID.randomUUID());
    var invalid = new BookingRequest(r.requestId(), 1L, 2L, r.checkIn(), r.checkIn());
    mvc.perform(
            post("/api/bookings")
                .with(
                    jwt()
                        .jwt(j -> j.subject(owner.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json")
                .content(json.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void schedulerRecoversOnlyStalePendingBookingsWithoutCallingHttp() {
    var started = appPending();
    var entity = bookings.findById(started.id()).orElseThrow();
    org.springframework.test.util.ReflectionTestUtils.setField(
        entity, "createdAt", Instant.now().minusSeconds(300));
    bookings.saveAndFlush(entity);
    new PendingBookingRecoveryScheduler(bookings, transactions(), Duration.ofMinutes(2)).run();
    assertThat(bookings.findById(started.id()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.CANCELLED);
    assertThat(tasks.findById(started.id() + "-2")).isPresent();
    verify(hotel, never()).reserve(anyLong(), anyString(), any());
  }

  @Autowired BookingTransactionService transactionService;

  BookingTransactionService transactions() {
    return transactionService;
  }

  BookingResponse appPending() {
    return transactionService.start(owner, request(UUID.randomUUID())).booking();
  }

  @Test
  void databaseEnforcesUniqueOwnerRequestKey() {
    var r = request(UUID.randomUUID());
    transactionService.start(owner, r);
    var duplicate =
        new Booking(
            users.findById(owner).orElseThrow(),
            r.requestId(),
            r.hotelId(),
            r.roomId(),
            r.roomId(),
            new StayPeriod(r.checkIn(), r.checkOut()));
    assertThatThrownBy(() -> bookings.saveAndFlush(duplicate))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(bookings.count()).isEqualTo(1);
  }
}
