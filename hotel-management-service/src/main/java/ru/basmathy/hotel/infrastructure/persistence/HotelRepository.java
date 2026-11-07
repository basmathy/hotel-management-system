package ru.basmathy.hotel.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.hotel.domain.model.Hotel;

public interface HotelRepository
    extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select h from Hotel h where h.id=:id")
  Optional<Hotel> findLockedById(@Param("id") Long id);
}
