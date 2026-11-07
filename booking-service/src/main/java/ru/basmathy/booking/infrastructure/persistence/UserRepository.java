package ru.basmathy.booking.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.booking.domain.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select u from User u where u.id=:id")
  Optional<User> findLockedById(@Param("id") Long id);
}
