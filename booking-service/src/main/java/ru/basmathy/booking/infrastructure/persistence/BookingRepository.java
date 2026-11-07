package ru.basmathy.booking.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.booking.domain.model.*;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByOwnerIdAndRequestId(Long ownerId, UUID requestId);

  Page<Booking> findByOwnerId(Long ownerId, Pageable pageable);

  boolean existsByOwnerId(Long ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> findLockedById(@Param("id") UUID id);

  @Query(
      "select b.id from Booking b where b.status=ru.basmathy.booking.domain.model.BookingStatus.PENDING and b.createdAt<:before order by b.createdAt")
  List<UUID> findStale(@Param("before") Instant before, Pageable pageable);
}
