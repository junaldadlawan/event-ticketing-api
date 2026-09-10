package com.junaldadlawan.event_ticketing_api.seatmap.repository;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findBySeatMapId(UUID seatMapId);
}
