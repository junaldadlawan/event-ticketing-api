package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.SalesOverTimeEntry;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Phase 14 (BR-ANALYTICS-001/002). Pure aggregation over existing
 * Order/Ticket/TicketType/Organization/Event data - no new entity/migration,
 * per the roadmap's own framing ("depends on Order/Ticket/Payout data
 * existing to aggregate").
 * <p>
 * {@code getEventAnalytics}'s admin bypass is a confirmed judgment call:
 * openapi.yaml's summary for this one endpoint says only "(owning
 * organizer)", unlike sibling owner/organizer-gated endpoints in this same
 * spec which explicitly say "or admin" (refund issuance, event-orders
 * listing, ticket check-in-record history). Read as a spec omission, not a
 * deliberate restriction - BR-AUTH-004 ("Admins have platform-wide access,
 * unscoped by organization or event") is a general rule, and every other
 * owner/organizer-gated endpoint already implemented in this codebase
 * (payouts, refunds, ticket types, templates, venues, resale/refund
 * policy...) includes the admin bypass with zero exceptions. Excluding
 * admin here alone would be inconsistent with the rest of this codebase for
 * no stated reason.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public EventAnalyticsResponse getEventAnalytics(UUID eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        long ticketsSold = ticketRepository.countByEventId(eventId);
        List<UUID> orderIds = ticketRepository.findDistinctOrderIdsByEventId(eventId);
        String currency = resolveEventCurrency(orderIds);
        long revenueAmount = orderIds.isEmpty() ? 0L : orderRepository.sumTotalAmountByIdIn(orderIds);

        int remainingInventory = ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId).stream()
                .mapToInt(TicketType::getQuantityAvailable)
                .sum();

        List<SalesOverTimeEntry> salesOverTime = buildSalesOverTime(eventId, orderIds, currency);

        return new EventAnalyticsResponse(eventId, (int) ticketsSold, new MoneyDto(revenueAmount, currency),
                remainingInventory, salesOverTime);
    }

    @Override
    public PlatformAnalyticsResponse getPlatformAnalytics() {
        accessGuard.requireAdmin();

        long totalGmvAmount = orderRepository.sumTotalAmount();
        String currency = orderRepository.findDistinctCurrencies().stream().findFirst().orElse(DEFAULT_CURRENCY);
        long activeOrganizers = organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.APPROVED);
        long eventVolume = eventRepository.countByDeletedAtIsNull();

        return new PlatformAnalyticsResponse(new MoneyDto(totalGmvAmount, currency), activeOrganizers, eventVolume);
    }

    /** Same "no multi-currency reconciliation precedent" reasoning as {@code getPlatformAnalytics} - see {@code OrderRepository.findDistinctCurrencies}. */
    private String resolveEventCurrency(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return DEFAULT_CURRENCY;
        }
        return orderRepository.findById(orderIds.get(0))
                .map(order -> order.getTotal().getCurrency())
                .orElse(DEFAULT_CURRENCY);
    }

    private List<SalesOverTimeEntry> buildSalesOverTime(UUID eventId, List<UUID> orderIds, String currency) {
        Map<java.time.LocalDate, Integer> ticketsByDay = new TreeMap<>();
        for (Object[] row : ticketRepository.countGroupedByDayForEvent(eventId)) {
            ticketsByDay.put(toLocalDate(row[0]), ((Number) row[1]).intValue());
        }

        Map<java.time.LocalDate, Long> revenueByDay = new TreeMap<>();
        if (!orderIds.isEmpty()) {
            for (Object[] row : orderRepository.sumTotalGroupedByDayForOrderIds(orderIds)) {
                revenueByDay.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
            }
        }

        List<SalesOverTimeEntry> entries = new ArrayList<>();
        for (java.time.LocalDate day : ticketsByDay.keySet()) {
            entries.add(new SalesOverTimeEntry(day, ticketsByDay.get(day),
                    new MoneyDto(revenueByDay.getOrDefault(day, 0L), currency)));
        }
        return entries;
    }

    /**
     * {@code cast(x as date)} in JPQL can come back as either {@code
     * java.time.LocalDate} or {@code java.sql.Date} depending on the JDBC
     * driver/Hibernate version - both types' {@code toString()} render
     * {@code yyyy-MM-dd}, so parsing that string is more robust than an
     * unconditional cast to one specific type.
     */
    private java.time.LocalDate toLocalDate(Object value) {
        return java.time.LocalDate.parse(value.toString());
    }

    /** Same idiom as {@code PayoutServiceImpl}/{@code RefundServiceImpl} (duplicated per-service - no shared guard method exists for this exact combination). */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the event's organizer/owner or an admin may view its analytics");
        }
    }
}
