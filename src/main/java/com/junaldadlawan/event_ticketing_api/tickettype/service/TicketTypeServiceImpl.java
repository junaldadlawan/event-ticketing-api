package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

    // openapi.yaml TicketTypeCreate: max_per_order default: 10.
    private static final int DEFAULT_MAX_PER_ORDER = 10;

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TicketRepository ticketRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    public TicketType create(UUID eventId, TicketTypeCreateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (!request.saleEndAt().isAfter(request.saleStartAt())) {
            throw new BadRequestException("saleEndAt must be after saleStartAt");
        }

        TicketType ticketType = TicketType.builder()
                .eventId(event.getId())
                .name(request.name())
                .kind(request.kind())
                .price(Money.builder()
                        .amount(request.price().amount())
                        .currency(request.price().currency())
                        .build())
                .quantityTotal(request.quantityTotal())
                .quantityAvailable(request.quantityTotal())
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .maxPerOrder(request.maxPerOrder() != null ? request.maxPerOrder() : DEFAULT_MAX_PER_ORDER)
                // New ticket types go to the end of the organizer's arrangement.
                .position(ticketTypeRepository.findMaxPositionByEventId(event.getId()) + 1)
                .build();
        return ticketTypeRepository.save(ticketType);
    }

    @Override
    public List<TicketType> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.DRAFT) {
            requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        }
        return ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId);
    }

    @Override
    public TicketType get(UUID ticketTypeId) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        Event event = getEventOrThrow(ticketType.getEventId());
        if (event.getStatus() == EventStatus.DRAFT) {
            requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        }
        return ticketType;
    }

    @Override
    public TicketType update(UUID ticketTypeId, TicketTypeUpdateRequest request) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Owning organizationId is always resolved from the persisted
        // ticket type's own event, never from client input (non-IDOR
        // pattern established in Phases 2-3).
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Ticket type name must not be blank");
            }
            ticketType.setName(request.name());
        }
        if (request.price() != null) {
            ticketType.setPrice(Money.builder()
                    .amount(request.price().amount())
                    .currency(request.price().currency())
                    .build());
        }
        if (request.quantityTotal() != null) {
            ticketType.setQuantityTotal(request.quantityTotal());
            // No checkout/hold logic exists yet to have decremented
            // quantityAvailable away from quantityTotal (Phase 5), so
            // re-syncing it here keeps the two fields consistent for now.
            // Flagged for revisit once inventory decrement lands.
            ticketType.setQuantityAvailable(request.quantityTotal());
        }
        if (request.saleStartAt() != null) {
            ticketType.setSaleStartAt(request.saleStartAt());
        }
        if (request.saleEndAt() != null) {
            ticketType.setSaleEndAt(request.saleEndAt());
        }
        if (request.maxPerOrder() != null) {
            ticketType.setMaxPerOrder(request.maxPerOrder());
        }
        if (request.salesPaused() != null) {
            ticketType.setSalesPaused(request.salesPaused());
        }

        if (!ticketType.getSaleEndAt().isAfter(ticketType.getSaleStartAt())) {
            throw new BadRequestException("saleEndAt must be after saleStartAt");
        }

        return ticketTypeRepository.save(ticketType);
    }

    @Override
    public List<TicketType> reorder(UUID eventId, List<UUID> orderedTicketTypeIds) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (new HashSet<>(orderedTicketTypeIds).size() != orderedTicketTypeIds.size()) {
            throw new BadRequestException("ticketTypeIds must not list the same ticket type twice");
        }
        List<TicketType> current = ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId);
        Map<UUID, TicketType> byId = new HashMap<>();
        for (TicketType ticketType : current) {
            byId.put(ticketType.getId(), ticketType);
        }
        // Exactly the event's ticket types: a stale screen (one added, deleted or foreign id) must not silently
        // drop a ticket type from the arrangement or touch another event's.
        if (!byId.keySet().equals(new HashSet<>(orderedTicketTypeIds))) {
            throw new BadRequestException("ticketTypeIds must list every ticket type of this event exactly once - reload the list and try again");
        }

        List<TicketType> arranged = new ArrayList<>();
        for (int position = 0; position < orderedTicketTypeIds.size(); position++) {
            TicketType ticketType = byId.get(orderedTicketTypeIds.get(position));
            ticketType.setPosition(position);
            arranged.add(ticketType);
        }
        List<TicketType> saved = ticketTypeRepository.saveAll(arranged);
        BusinessAuditLogger.record("ticket_type.reordered", "Event", eventId, BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    @Override
    public void delete(UUID ticketTypeId) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Same rule as update: the owning organization comes from the ticket type's own event.
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        // Anything sold or held stays referenced by carts, orders and tickets, so it can only be paused.
        boolean hasSalesOrHolds = ticketType.getQuantityAvailable() != ticketType.getQuantityTotal()
                || cartItemRepository.existsByTicketTypeId(ticketTypeId)
                || ticketRepository.existsByTicketTypeId(ticketTypeId);
        if (hasSalesOrHolds) {
            throw new ConflictException("This ticket type already has sales or held tickets, so it can't be deleted. Pause its sales instead.");
        }

        ticketType.markDeleted();
        ticketTypeRepository.save(ticketType);
        BusinessAuditLogger.record("ticket_type.deleted", "TicketType", ticketTypeId, BusinessAuditLogger.Outcome.SUCCESS);
    }

    @Override
    public TicketType setSalesStatus(UUID ticketTypeId, SalesStatus status) {
        return setSalesPaused(ticketTypeId, status == SalesStatus.PAUSED);
    }

    /**
     * The one switch behind both actions (and the {@code salesPaused} flag of the PATCH). Allowed whatever the
     * event's status - it is only a flag; whether anything can be bought is decided at cart and checkout time.
     * Asking for the state it is already in is not an error and writes nothing.
     */
    private TicketType setSalesPaused(UUID ticketTypeId, boolean paused) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Same rule as update/delete: the owning organization comes from the ticket type's own event.
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        if (ticketType.isSalesPaused() == paused) {
            return ticketType;
        }
        ticketType.setSalesPaused(paused);
        TicketType saved = ticketTypeRepository.save(ticketType);
        BusinessAuditLogger.record(paused ? "ticket_type.sales_paused" : "ticket_type.sales_resumed", "TicketType",
                ticketTypeId, BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    public TicketType getOrThrow(UUID ticketTypeId) {
        return ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * Mirrors {@code EventServiceImpl.requireOwnerOrOrganizerOrAdmin}
     * exactly (BR-AUTH-004 admin bypass, then OWNER/ORGANIZER on the
     * event's own organization).
     */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type");
        }
    }
}
