package ru.basmathy.booking.presentation.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.basmathy.booking.application.service.BookingApplicationService;
import ru.basmathy.booking.domain.model.BookingStatus;
import ru.basmathy.booking.presentation.dto.*;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
@RequiredArgsConstructor
public class BookingController {
  private final BookingApplicationService bookings;

  @PostMapping
  public ResponseEntity<BookingResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
    var result = bookings.create(Long.valueOf(jwt.getSubject()), request);
    return ResponseEntity.status(
            result.status() == BookingStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
        .body(result);
  }

  @GetMapping
  public Page<BookingResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      Authentication auth,
      @PageableDefault(size = 20, sort = "createdAt") Pageable page) {
    return bookings.list(Long.valueOf(jwt.getSubject()), admin(auth), page);
  }

  @GetMapping("/{id}")
  public BookingResponse get(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    return bookings.get(id, Long.valueOf(jwt.getSubject()), admin(auth));
  }

  @PostMapping("/{id}/cancel")
  public BookingResponse cancel(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
    return bookings.cancel(id, Long.valueOf(jwt.getSubject()), admin(auth));
  }

  private boolean admin(Authentication a) {
    return a.getAuthorities().stream().anyMatch(v -> v.getAuthority().equals("ROLE_ADMIN"));
  }
}
