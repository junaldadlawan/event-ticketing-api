package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAudienceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventSalesPatternsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventFunnelResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareTimelineResponse;
import com.junaldadlawan.event_ticketing_api.analytics.repository.ShareCounterRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.EventInsightsRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final EventInsightsRepository insightsRepository;
    private final FunnelRepository funnelRepository;
    private final ShareCounterRepository shareCounterRepository;

    static final int MAX_FUNNEL_DAYS = 366;

    static final List<String> AGE_GROUPS = List.of("Under 18", "18-24", "25-34", "35-44", "45-54", "55+");
    /** A group with fewer buyers than this is shown as "fewer than 5" (null counts), so no single buyer can be picked out. */
    static final int MIN_GROUP_SIZE = 5;

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
                remainingInventory, salesOverTime, event.getShareClickCount(), event.getShareCopyCount(),
                event.getShareQrCount(), event.getShareLinkOpenCount() + event.getShareQrScanCount(), event.getShareLinkOpenCount(),
                event.getShareQrScanCount(), event.getShareLinkBuyCount(), event.getShareQrBuyCount());
    }

    @Override
    public EventAudienceResponse getEventAudience(UUID eventId) {
        requireEventAccess(eventId);
        List<EventInsightsRepository.AudienceBuyer> buyers = insightsRepository.audience(eventId);
        Map<String, long[]> groups = new HashMap<>();   // label -> {buyers, tickets}
        long unknownAge = 0;
        for (EventInsightsRepository.AudienceBuyer buyer : buyers) {
            if (buyer.ageGroup() == null) {
                unknownAge++;
                continue;
            }
            long[] group = groups.computeIfAbsent(buyer.ageGroup(), label -> new long[2]);
            group[0]++;
            group[1] += buyer.tickets();
        }
        Set<String> hidden = new HashSet<>();
        AGE_GROUPS.stream().filter(label -> buyersIn(groups, label) > 0 && buyersIn(groups, label) < MIN_GROUP_SIZE).forEach(hidden::add);
        if (hidden.size() == 1) {
            // the total and every other group are shown, so one hidden group could be worked out by subtraction: hide the next smallest too
            AGE_GROUPS.stream().filter(label -> !hidden.contains(label) && buyersIn(groups, label) > 0)
                    .min(Comparator.comparingLong(label -> buyersIn(groups, label))).ifPresent(hidden::add);
        }
        return new EventAudienceResponse(eventId, buyers.size(), unknownAge, AGE_GROUPS.stream()
                .map(label -> hidden.contains(label) ? new EventAudienceResponse.AgeGroup(label, null, null)
                        : new EventAudienceResponse.AgeGroup(label, buyersIn(groups, label), groups.getOrDefault(label, new long[2])[1]))
                .toList());
    }

    private static long buyersIn(Map<String, long[]> groups, String label) {
        return groups.getOrDefault(label, new long[2])[0];
    }

    @Override
    public EventSalesPatternsResponse getEventSalesPatterns(UUID eventId) {
        Event event = requireEventAccess(eventId);
        String timezone = event.getTimezone() == null || event.getTimezone().isBlank() ? "UTC" : event.getTimezone();
        ZoneId zone = ZoneId.of(timezone);
        long tickets = 0;
        long orders = 0;
        long promoOrders = 0;
        long[] byWeekday = new long[7];
        long[] byHour = new long[24];
        TreeMap<LocalDate, Long> byDay = new TreeMap<>();
        for (EventInsightsRepository.SalesSlot slot : insightsRepository.salesSlots(eventId, timezone)) {
            tickets += slot.tickets();
            orders += slot.orders();
            promoOrders += slot.promoOrders();
            byWeekday[slot.isoWeekday() - 1] += slot.tickets();
            byHour[slot.hour()] += slot.tickets();
            byDay.merge(LocalDate.parse(slot.day()), slot.tickets(), Long::sum);
        }
        List<EventSalesPatternsResponse.Day> daily = new ArrayList<>();
        if (!byDay.isEmpty()) {
            LocalDate last = LocalDate.now(zone);
            if (event.getEndAt() != null && event.getEndAt().atZone(zone).toLocalDate().isBefore(last)) {
                last = event.getEndAt().atZone(zone).toLocalDate();
            }
            for (LocalDate day = byDay.firstKey(); !day.isAfter(last); day = day.plusDays(1)) {
                daily.add(new EventSalesPatternsResponse.Day(day, byDay.getOrDefault(day, 0L)));
            }
        }
        return new EventSalesPatternsResponse(eventId, timezone, tickets, orders, promoOrders, daily, byWeekday, byHour);
    }

    @Override
    public EventFunnelResponse getEventFunnel(UUID eventId, LocalDate from, LocalDate to) {
        Event event = requireEventAccess(eventId);
        String timezone = event.getTimezone() == null || event.getTimezone().isBlank() ? "UTC" : event.getTimezone();
        ZoneId zone = ZoneId.of(timezone);
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate start = from;
        if (start == null) {
            // default: since the event was created, but never more than the longest range allowed
            start = event.getCreatedAt() == null ? end : event.getCreatedAt().atZone(zone).toLocalDate();
            if (start.isAfter(end)) {
                start = end;
            }
            if (start.plusDays(MAX_FUNNEL_DAYS - 1L).isBefore(end)) {
                start = end.minusDays(MAX_FUNNEL_DAYS - 1L);
            }
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        if (start.plusDays(MAX_FUNNEL_DAYS - 1L).isBefore(end)) {
            throw new BadRequestException("The range must not be longer than " + MAX_FUNNEL_DAYS + " days");
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();

        FunnelRepository.ViewTotals views = funnelRepository.viewTotals(eventId, fromInstant, toInstant);
        long buyersAdding = funnelRepository.uniqueBuyers(eventId, fromInstant, toInstant, FunnelRepository.CartAction.ADD);
        long buyersCheckingOut = funnelRepository.uniqueBuyers(eventId, fromInstant, toInstant, FunnelRepository.CartAction.CHECKOUT);
        // ticket type -> action -> total
        Map<UUID, Map<String, FunnelRepository.ActivityTotal>> activity = new HashMap<>();
        for (FunnelRepository.ActivityTotal total : funnelRepository.activityByTicketType(eventId, fromInstant, toInstant)) {
            activity.computeIfAbsent(total.ticketTypeId(), id -> new HashMap<>()).put(total.action(), total);
        }
        long addActions = 0;
        long added = 0;
        long checkedOut = 0;
        for (Map<String, FunnelRepository.ActivityTotal> byAction : activity.values()) {
            FunnelRepository.ActivityTotal add = byAction.get("ADD");
            FunnelRepository.ActivityTotal checkout = byAction.get("CHECKOUT");
            addActions += add == null ? 0 : add.actions();
            added += add == null ? 0 : add.quantity();
            checkedOut += checkout == null ? 0 : checkout.quantity();
        }
        List<EventFunnelResponse.TicketTypeFunnel> ticketTypes = ticketTypeRepository.findByEventIdAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(eventId)
                .stream().map(type -> {
                    Map<String, FunnelRepository.ActivityTotal> byAction = activity.getOrDefault(type.getId(), Map.of());
                    long typeAdded = quantity(byAction, "ADD");
                    long typeCheckedOut = quantity(byAction, "CHECKOUT");
                    FunnelRepository.ActivityTotal add = byAction.get("ADD");
                    return new EventFunnelResponse.TicketTypeFunnel(type.getId(), type.getName(), add == null ? 0 : add.actions(), typeAdded,
                            add == null ? 0 : add.uniqueBuyers(), typeCheckedOut, quantity(byAction, "REMOVE"), quantity(byAction, "EXPIRE"),
                            typeAdded == 0 ? null : (double) typeCheckedOut / typeAdded);
                }).toList();

        Map<LocalDate, long[]> days = new TreeMap<>();   // day -> {views, visitors, added, checked out}
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.put(day, new long[4]);
        }
        for (FunnelRepository.DayViews day : funnelRepository.viewsByDay(eventId, fromInstant, toInstant, timezone)) {
            long[] counts = days.get(LocalDate.parse(day.day()));
            if (counts != null) {
                counts[0] = day.views();
                counts[1] = day.uniqueVisitors();
            }
        }
        for (FunnelRepository.DayActivity day : funnelRepository.activityByDay(eventId, fromInstant, toInstant, timezone)) {
            long[] counts = days.get(LocalDate.parse(day.day()));
            if (counts != null) {
                counts["ADD".equals(day.action()) ? 2 : 3] = day.quantity();
            }
        }
        List<EventFunnelResponse.Day> daily = days.entrySet().stream()
                .map(e -> new EventFunnelResponse.Day(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])).toList();

        Double viewToCart = views.uniqueVisitors() == 0 ? null : (double) buyersAdding / views.uniqueVisitors();
        Double viewToPurchase = views.uniqueVisitors() == 0 ? null : (double) buyersCheckingOut / views.uniqueVisitors();
        return new EventFunnelResponse(eventId, timezone, start, end, views.views(), views.uniqueVisitors(),
                new EventFunnelResponse.AddToCart(addActions, added, buyersAdding), new EventFunnelResponse.CheckedOut(checkedOut, buyersCheckingOut),
                viewToCart, viewToPurchase, daily, ticketTypes, funnelRepository.trackingSince());
    }

    private static long quantity(Map<String, FunnelRepository.ActivityTotal> byAction, String action) {
        FunnelRepository.ActivityTotal total = byAction.get(action);
        return total == null ? 0 : total.quantity();
    }

    @Override
    public ShareTimelineResponse getEventShareTimeline(UUID eventId, LocalDate from, LocalDate to) {
        Event event = requireEventAccess(eventId);
        String timezone = event.getTimezone() == null || event.getTimezone().isBlank() ? "UTC" : event.getTimezone();
        ZoneId zone = ZoneId.of(timezone);
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate trackingSince = shareCounterRepository.firstDay(eventId);
        LocalDate start = from;
        if (start == null) {
            // default: the day counting began (else the event's creation), never more than the longest range allowed
            start = trackingSince != null ? trackingSince
                    : event.getCreatedAt() == null ? end : event.getCreatedAt().atZone(zone).toLocalDate();
            if (start.isAfter(end)) {
                start = end;
            }
            if (start.plusDays(MAX_FUNNEL_DAYS - 1L).isBefore(end)) {
                start = end.minusDays(MAX_FUNNEL_DAYS - 1L);
            }
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        if (start.plusDays(MAX_FUNNEL_DAYS - 1L).isBefore(end)) {
            throw new BadRequestException("The range must not be longer than " + MAX_FUNNEL_DAYS + " days");
        }
        Map<LocalDate, ShareCounterRepository.Day> rows = new HashMap<>();
        shareCounterRepository.days(eventId, start, end).forEach(row -> rows.put(row.day(), row));
        List<ShareTimelineResponse.Day> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            ShareCounterRepository.Day row = rows.get(day);
            days.add(row == null ? new ShareTimelineResponse.Day(day, 0, 0, 0, 0, 0, 0, 0)
                    : new ShareTimelineResponse.Day(day, row.shareButton(), row.copyLink(), row.qrGenerate(), row.linkOpen(), row.qrScan(),
                            row.linkBuy(), row.qrBuy()));
        }
        Map<Integer, ShareCounterRepository.Hour> hourRows = new HashMap<>();
        shareCounterRepository.hours(eventId, start, end).forEach(row -> hourRows.put(row.hour(), row));
        List<ShareTimelineResponse.Hour> byHour = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            ShareCounterRepository.Hour row = hourRows.get(hour);
            byHour.add(row == null ? new ShareTimelineResponse.Hour(hour, 0, 0, 0, 0, 0, 0, 0)
                    : new ShareTimelineResponse.Hour(hour, row.shareButton(), row.copyLink(), row.qrGenerate(), row.linkOpen(), row.qrScan(),
                            row.linkBuy(), row.qrBuy()));
        }
        return new ShareTimelineResponse(timezone, trackingSince, days, byHour);
    }

    private Event requireEventAccess(UUID eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());
        return event;
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
                || accessGuard.hasPermission(callerId, organizationId, OrganizationPermission.ANALYTICS);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the event's organizer/owner or an admin may view its analytics");
        }
    }
}
