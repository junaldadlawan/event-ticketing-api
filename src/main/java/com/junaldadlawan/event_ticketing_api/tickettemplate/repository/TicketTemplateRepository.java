package com.junaldadlawan.event_ticketing_api.tickettemplate.repository;

import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTemplateRepository extends JpaRepository<TicketTemplate, UUID> {

    List<TicketTemplate> findByEventIdAndDeletedAtIsNull(UUID eventId);

    Optional<TicketTemplate> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Ticket-type-specific template resolution for artifact rendering —
     * preferred over the event-level fallback below when both exist for a
     * given ticket (Phase 6b confirmed decision #5).
     */
    Optional<TicketTemplate> findByEventIdAndTicketTypeIdAndFormatAndDeletedAtIsNull(
            UUID eventId, UUID ticketTypeId, TicketTemplateFormat format);

    /**
     * Event-level fallback (a template with no {@code ticketTypeId} applies
     * to the whole event) — used when no ticket-type-specific template
     * matches.
     */
    Optional<TicketTemplate> findByEventIdAndTicketTypeIdIsNullAndFormatAndDeletedAtIsNull(
            UUID eventId, TicketTemplateFormat format);
}
