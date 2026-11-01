package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room,UUID> {
    List<Room> findAllByOrderByNameAscIdAsc(Pageable page);
}
