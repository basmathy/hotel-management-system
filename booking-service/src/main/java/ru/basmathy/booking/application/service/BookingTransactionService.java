package ru.basmathy.booking.application.service;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.basmathy.booking.domain.model.*;
import ru.basmathy.booking.domain.policy.BookingAccessPolicy;
import ru.basmathy.booking.infrastructure.persistence.*;
import ru.basmathy.booking.presentation.dto.*;

@Service
@RequiredArgsConstructor
public class BookingTransactionService {
  private static final Logger log = LoggerFactory.getLogger(BookingTransactionService.class);
  private final BookingRepository bookings;
  private final UserRepository users;
  private final CompensationTaskRepository tasks;
  private final BookingMapper mapper;
  private final BookingAccessPolicy accessPolicy;

  public record Started(BookingResponse booking, boolean created) {}

  @Transactional
  public Started start(Long userId, BookingRequest request) {
    User user =
        users.findLockedById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    StayPeriod period = new StayPeriod(request.checkIn(), request.checkOut());
    var existing = bookings.findByOwnerIdAndRequestId(userId, request.requestId());
    if (existing.isPresent()) {
      Booking b = existing.get();
      if (!b.matches(request.hotelId(), request.roomId(), period))
        throw new DomainConflict("Request ID reused with different parameters");
      return new Started(mapper.toResponse(b), false);
    }
    if (period.getCheckIn().isBefore(LocalDate.now()))
      throw new IllegalArgumentException("Check-in cannot be in the past");
    Booking b =
        bookings.saveAndFlush(
            new Booking(
                user,
                request.requestId(),
                request.hotelId(),
                request.roomId(),
                request.roomId(),
                period));
    log.info("Saga started booking={} status=PENDING", b.getId());
    return new Started(mapper.toResponse(b), true);
  }

  @Transactional
  public boolean selectRoom(UUID id, Long roomId) {
    Booking b = locked(id);
    if (b.getStatus() != BookingStatus.PENDING) return false;
    b.selectRoom(roomId);
    return true;
  }

  @Transactional
  public BookingResponse confirm(UUID id) {
    Booking b = locked(id);
    if (b.getStatus() == BookingStatus.PENDING) {
      b.confirm();
      log.info("Booking status changed booking={} status=CONFIRMED", id);
    }
    return mapper.toResponse(b);
  }

  @Transactional
  public BookingResponse cancel(UUID id, Long userId, boolean admin) {
    Booking b = locked(id);
    if (userId != null) accessPolicy.verify(b, userId, admin);
    cancelAndEnqueue(b);
    return mapper.toResponse(b);
  }

  @Transactional
  public void recover(UUID id, Instant before) {
    Booking b = locked(id);
    if (b.getStatus() == BookingStatus.PENDING && b.getCreatedAt().isBefore(before))
      cancelAndEnqueue(b);
  }

  private void cancelAndEnqueue(Booking b) {
    if (b.cancel()) log.info("Booking status changed booking={} status=CANCELLED", b.getId());
    if (b.getRoomId() != null && !tasks.existsById(b.operationId()))
      tasks.save(new CompensationTask(b));
  }

  @Transactional(readOnly = true)
  public BookingResponse read(UUID id, Long userId, boolean admin) {
    Booking b = bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking not found"));
    if (userId != null) accessPolicy.verify(b, userId, admin);
    return mapper.toResponse(b);
  }

  @Transactional(readOnly = true)
  public Page<BookingResponse> list(Long owner, boolean admin, Pageable page) {
    return (admin ? bookings.findAll(page) : bookings.findByOwnerId(owner, page))
        .map(mapper::toResponse);
  }

  private Booking locked(UUID id) {
    return bookings
        .findLockedById(id)
        .orElseThrow(() -> new NotFoundException("Booking not found"));
  }
}
