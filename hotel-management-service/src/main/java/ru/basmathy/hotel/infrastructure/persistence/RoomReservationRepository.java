package ru.basmathy.hotel.infrastructure.persistence;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.basmathy.hotel.domain.model.*;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, String> {
  boolean existsByRoomId(Long roomId);

  @Query(
      "select count(v)>0 from RoomReservation v where v.room.id=:roomId and v.status=ru.basmathy.hotel.domain.model.ReservationStatus.HELD and v.period.checkIn<:end and v.period.checkOut>:start")
  boolean overlaps(
      @Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select v from RoomReservation v where v.room.id=:roomId and v.status=ru.basmathy.hotel.domain.model.ReservationStatus.HELD and v.period.checkIn<:end and v.period.checkOut>:start")
  List<RoomReservation> overlapping(
      @Param("roomId") Long roomId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
