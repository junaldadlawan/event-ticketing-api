package com.junaldadlawan.event_ticketing_api.tickettype.repository;

import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdAndDeletedAtIsNull(UUID eventId);

    Optional<TicketType> findByIdAndDeletedAtIsNull(UUID id);
}
