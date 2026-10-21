package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;
import com.junaldadlawan.event_ticketing_api.platformsetting.service.TicketSaleSettingsService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeCreateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.TicketTypeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.SalesStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

    // openapi.yaml TicketTypeCreate: max_per_order default: 10.
    private static final int DEFAULT_MAX_PER_ORDER = 10;

    private final TicketTypeRepository ticketTypeRepository;
    // BR-EVENT-003 / BR-ADMIN-SETTINGS-002: how long a sale window must run and how early it must close
    private final TicketSaleSettingsService ticketSaleSettingsService;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TicketRepository ticketRepository;
    private final CartItemRepository cartItemRepository;
    private final EventCompletionService eventCompletionService;
    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    @Override
    public TicketType create(UUID eventId, TicketTypeCreateRequest request) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        EventCompletionService.requireNotEnded(event);

        // omitted = start now, on the server's clock rather than the buyer's
        Instant saleStartAt = request.saleStartAt() != null ? request.saleStartAt() : Instant.now();
        requireSaleWindowRules(event, saleStartAt, request.saleEndAt());
        // with a per-buyer limit below the usual default, the default per-order limit follows it down
        int maxPerOrder = request.maxPerOrder() != null ? request.maxPerOrder()
                : request.maxPerUser() != null ? Math.min(DEFAULT_MAX_PER_ORDER, request.maxPerUser()) : DEFAULT_MAX_PER_ORDER;
        requireMaxPerOrderWithinPerUser(maxPerOrder, request.maxPerUser());

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
                .saleStartAt(saleStartAt)
                .saleEndAt(request.saleEndAt())
                .maxPerOrder(maxPerOrder)
                .maxPerUser(request.maxPerUser())
                .gateOpensMinutesBefore(request.gateOpensMinutesBefore())
                .color(upper(request.color()))
                // New ticket types go to the end of the organizer's arrangement.
                .position(ticketTypeRepository.findMaxPositionByEventId(event.getId()) + 1)
                .build();
        TicketType created = ticketTypeRepository.save(ticketType);
        auditLogService.recordByCurrentUser("ticket_type.created", "TicketType", created.getId(), created.getEventId(), AuditDetails.named(ticketType.getName()));
        return created;
    }

    @Override
    public List<TicketType> list(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.DRAFT) {
            requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        }
        // the page that lists ticket types shows the event as over the moment it ends, not at the next sweep
        eventCompletionService.completeIfEnded(event);
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
    @Transactional
    public TicketType update(UUID ticketTypeId, TicketTypeUpdateRequest request) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Owning organizationId is always resolved from the persisted
        // ticket type's own event, never from client input (non-IDOR
        // pattern established in Phases 2-3).
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        EventCompletionService.requireNotEnded(event);
        Map<String, Object> before = AuditDetails.snapshot(ticketType, TicketTypeUpdateRequest.class);
        if (request.quantityTotal() != null) {
            // under the row lock holds take, re-read before anything is changed, so a sale made since the row was loaded is not overwritten
            ticketTypeRepository.findByIdForUpdate(ticketTypeId);
            entityManager.refresh(ticketType);
        }

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
            // Holds and sales keep only quantityAvailable: what is sold or held is total - available. Resetting available to the new
            // total would hand those tickets out again, so the new total keeps them taken.
            int taken = ticketType.getQuantityTotal() - ticketType.getQuantityAvailable();
            if (request.quantityTotal() < taken) {
                throw new ConflictException("quantityTotal cannot be lower than the " + taken + " tickets already sold or held");
            }
            ticketType.setQuantityTotal(request.quantityTotal());
            ticketType.setQuantityAvailable(request.quantityTotal() - taken);
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
        if (request.color() != null) {
            ticketType.setColor(upper(request.color().orElse(null)));
        }
        if (request.salesPaused() != null) {
            ticketType.setSalesPaused(request.salesPaused());
        }
        if (request.maxPerUser() != null) {
            ticketType.setMaxPerUser(request.maxPerUser().orElse(null));
        }
        if (request.gateOpensMinutesBefore() != null) {
            ticketType.setGateOpensMinutesBefore(request.gateOpensMinutesBefore().orElse(null));
        }

        // BR-ADMIN-SETTINGS-002: the current rules apply to a sale window being edited; an untouched one is left as it was
        if (request.saleStartAt() != null || request.saleEndAt() != null) {
            requireSaleWindowRules(event, ticketType.getSaleStartAt(), ticketType.getSaleEndAt());
        }
        requireMaxPerOrderWithinPerUser(ticketType.getMaxPerOrder(), ticketType.getMaxPerUser());

        TicketType updated = ticketTypeRepository.save(ticketType);
        // BR-EVENT-006: only the fields that actually changed; an update that changed nothing is not logged
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(ticketType, TicketTypeUpdateRequest.class));
        if (changes != null) {
            auditLogService.recordByCurrentUser("ticket_type.updated", "TicketType", ticketTypeId, ticketType.getEventId(), changes);
        }
        return updated;
    }

    private static String upper(String color) {
        return color == null ? null : color.toUpperCase(Locale.ROOT);
    }

    /**
     * BR-EVENT-003 with BR-ADMIN-SETTINGS-002's current values: the window runs for at least the minimum duration (which
     * also covers end > start) and closes at least the set time before the event starts.
     */
    // shortcut: checked only on ticket-type create/update because an event's startAt can't change today; if EventUpdateRequest ever takes startAt, clamp or reject this event's ticket types in EventServiceImpl.update.
    private void requireSaleWindowRules(Event event, Instant saleStartAt, Instant saleEndAt) {
        TicketSaleSettings settings = ticketSaleSettingsService.current();
        if (saleEndAt.isBefore(saleStartAt.plus(Duration.ofMinutes(settings.getMinSaleDurationMinutes())))) {
            throw new BadRequestException("Ticket sales must run for at least " + describe(settings.getMinSaleDurationMinutes()));
        }
        Instant latestEnd = event.getStartAt().minus(Duration.ofMinutes(settings.getSalesCloseMinutesBeforeEvent()));
        if (saleEndAt.isAfter(latestEnd)) {
            throw new BadRequestException("Ticket sales must end at least " + describe(settings.getSalesCloseMinutesBeforeEvent())
                    + " before the event starts (by " + latestEnd + ")");
        }
    }

    private static void requireMaxPerOrderWithinPerUser(int maxPerOrder, Integer maxPerUser) {
        if (maxPerUser != null && maxPerOrder > maxPerUser) {
            throw new BadRequestException("Max per order can't be more than the per-buyer limit");
        }
    }

    /** "1 hour", "2 hours", "90 minutes", "1 minute". */
    static String describe(int minutes) {
        if (minutes > 0 && minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    @Override
    public List<TicketType> reorder(UUID eventId, List<UUID> orderedTicketTypeIds) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        EventCompletionService.requireNotEnded(event);

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
        Map<String, Object> details = AuditDetails.named(event.getTitle());
        details.put("order", arranged.stream().map(TicketType::getName).toList());
        auditLogService.recordByCurrentUser("ticket_type.reordered", "Event", eventId, eventId, details);
        return saved;
    }

    @Override
    public void delete(UUID ticketTypeId) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Same rule as update: the owning organization comes from the ticket type's own event.
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        EventCompletionService.requireNotEnded(event);

        // Anything sold or held stays referenced by carts, orders and tickets, so it can only be paused.
        boolean hasSalesOrHolds = ticketType.getQuantityAvailable() != ticketType.getQuantityTotal()
                || cartItemRepository.existsByTicketTypeId(ticketTypeId)
                || ticketRepository.existsByTicketTypeId(ticketTypeId);
        if (hasSalesOrHolds) {
            throw new ConflictException("This ticket type already has sales or held tickets, so it can't be deleted. Pause its sales instead.");
        }

        ticketType.markDeleted();
        ticketTypeRepository.save(ticketType);
        auditLogService.recordByCurrentUser("ticket_type.deleted", "TicketType", ticketTypeId, ticketType.getEventId(), AuditDetails.named(ticketType.getName()));
    }

    @Override
    public TicketType setSalesStatus(UUID ticketTypeId, SalesStatus status) {
        return setSalesPaused(ticketTypeId, status == SalesStatus.PAUSED);
    }

    /**
     * The one switch behind both actions (and the {@code salesPaused} flag of the PATCH). Allowed whatever the
     * event's status, except once the event has ended (BR-EVENT-005) - it is only a flag; whether anything can
     * be bought is decided at cart and checkout time. Asking for the state it is already in is not an error and
     * writes nothing.
     */
    private TicketType setSalesPaused(UUID ticketTypeId, boolean paused) {
        TicketType ticketType = getOrThrow(ticketTypeId);
        // Same rule as update/delete: the owning organization comes from the ticket type's own event.
        Event event = getEventOrThrow(ticketType.getEventId());
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        EventCompletionService.requireNotEnded(event);

        if (ticketType.isSalesPaused() == paused) {
            return ticketType;
        }
        ticketType.setSalesPaused(paused);
        TicketType saved = ticketTypeRepository.save(ticketType);
        auditLogService.recordByCurrentUser(paused ? "ticket_type.sales_paused" : "ticket_type.sales_resumed",
                "TicketType", ticketTypeId, ticketType.getEventId(), AuditDetails.named(ticketType.getName()));
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
                || accessGuard.hasPermission(callerId, organizationId, OrganizationPermission.EVENTS);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this ticket type");
        }
    }
}
