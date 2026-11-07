package ru.basmathy.booking.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.booking.domain.model.CompensationTask;

public interface CompensationTaskRepository extends JpaRepository<CompensationTask, String> {
  @Query(
      "select t.operationId from CompensationTask t where t.completed=false and t.nextAttemptAt<=:now order by t.nextAttemptAt")
  List<String> findDue(@Param("now") Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from CompensationTask t where t.operationId=:id")
  Optional<CompensationTask> findLockedById(@Param("id") String id);
}
