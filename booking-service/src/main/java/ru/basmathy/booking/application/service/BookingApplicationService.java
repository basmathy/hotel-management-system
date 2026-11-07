package ru.basmathy.booking.application.service;

import java.util.UUID;
import org.springframework.data.domain.*;
import ru.basmathy.booking.presentation.dto.*;

public interface BookingApplicationService {
  BookingResponse create(Long userId, BookingRequest request);

  BookingResponse get(UUID id, Long userId, boolean admin);

  Page<BookingResponse> list(Long userId, boolean admin, Pageable page);

  BookingResponse cancel(UUID id, Long userId, boolean admin);
}
