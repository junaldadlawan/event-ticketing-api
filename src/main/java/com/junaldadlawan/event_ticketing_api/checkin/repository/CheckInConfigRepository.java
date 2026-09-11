package com.junaldadlawan.event_ticketing_api.checkin.repository;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CheckInConfigRepository extends JpaRepository<CheckInConfig, UUID> {
    Optional<CheckInConfig> findByEventId(UUID eventId);
}
