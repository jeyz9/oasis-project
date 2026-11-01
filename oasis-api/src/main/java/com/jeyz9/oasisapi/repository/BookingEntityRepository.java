package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface BookingEntityRepository extends JpaRepository<Booking,UUID> {
    @Query(value="SELECT * FROM bookings WHERE id=:id FOR NO KEY UPDATE",nativeQuery=true)
    Optional<Booking> lockById(UUID id);
}
