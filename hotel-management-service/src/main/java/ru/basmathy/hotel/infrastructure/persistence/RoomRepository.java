package ru.basmathy.hotel.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.hotel.domain.model.*;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select r from Room r where r.id=:id")
  Optional<Room> findLockedById(@Param("id") Long id);

  boolean existsByHotelId(Long hotelId);

  List<Room> findByHotelId(Long hotelId);

  @Query(
      "select r from Room r join fetch r.hotel where r.operational=true and (:hotelId is null or r.hotel.id=:hotelId) and r.capacity>=:capacity and not exists (select v from RoomReservation v where v.room=r and v.status=ru.basmathy.hotel.domain.model.ReservationStatus.HELD and v.period.checkIn<:end and v.period.checkOut>:start)")
  List<Room> available(
      @Param("hotelId") Long hotelId,
      @Param("capacity") int capacity,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}
