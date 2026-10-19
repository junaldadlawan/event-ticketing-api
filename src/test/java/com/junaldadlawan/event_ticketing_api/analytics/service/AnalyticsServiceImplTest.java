package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAudienceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.EventSalesPatternsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.PlatformAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.repository.EventInsightsRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link AnalyticsServiceImpl} (no Spring context) -
 * covers BR-ANALYTICS-001/002: the owning-organizer/admin gate for event
 * analytics, admin-only gate for platform analytics, the metric
 * computations themselves, and the sales-over-time day-merge logic
 * (including days present on only one side of the ticket/revenue union).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private com.junaldadlawan.event_ticketing_api.analytics.repository.EventInsightsRepository insightsRepository;
    @Mock
    private com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository funnelRepository;

    private AnalyticsServiceImpl service;

    private UUID eventId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new AnalyticsServiceImpl(eventRepository, orderRepository, ticketRepository, ticketTypeRepository, organizationRepository, accessGuard, insightsRepository, funnelRepository);
        eventId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    private Event event() {
        return Event.builder().id(eventId).organizationId(orgId).title("t").build();
    }

    // ---- getEventAnalytics ----

    @Test
    void getEventAnalytics_unknownEvent_throwsResourceNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventAnalytics(eventId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getEventAnalytics_roselessStranger_throwsForbidden() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(false);
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getEventAnalytics(eventId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getEventAnalytics_admin_bypassesOwnershipCheck() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(0L);
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of());
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());
        when(ticketRepository.countGroupedByDayForEvent(eventId)).thenReturn(List.of());

        EventAnalyticsResponse response = service.getEventAnalytics(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
    }

    @Test
    void getEventAnalytics_computesMetricsFromRepositories() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(42L);

        UUID orderId = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(orderId));
        when(orderRepository.sumTotalAmountByIdIn(List.of(orderId))).thenReturn(500000L);
        Order order = Order.builder().id(orderId).total(Money.builder().amount(500000L).currency("USD").build()).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        TicketType gaType = TicketType.builder().eventId(eventId).quantityAvailable(10).build();
        TicketType reservedType = TicketType.builder().eventId(eventId).quantityAvailable(5).build();
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of(gaType, reservedType));

        when(ticketRepository.countGroupedByDayForEvent(eventId)).thenReturn(List.of());
        lenient().when(orderRepository.sumTotalGroupedByDayForOrderIds(List.of(orderId))).thenReturn(List.of());

        EventAnalyticsResponse response = service.getEventAnalytics(eventId);

        assertThat(response.ticketsSold()).isEqualTo(42);
        assertThat(response.revenue().amount()).isEqualTo(500000L);
        assertThat(response.revenue().currency()).isEqualTo("USD");
        assertThat(response.remainingInventory()).isEqualTo(15);
    }

    @Test
    void getEventAnalytics_salesOverTime_mergesTicketAndRevenueDays() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.countByEventId(eventId)).thenReturn(2L);

        UUID orderId = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(orderId));
        when(orderRepository.sumTotalAmountByIdIn(List.of(orderId))).thenReturn(1000L);
        Order order = Order.builder().id(orderId).total(Money.builder().amount(1000L).currency("USD").build()).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        LocalDate day1 = LocalDate.of(2026, 1, 1);
        LocalDate day2 = LocalDate.of(2026, 1, 2);
        // day1 has a ticket sold but the query for that same day's revenue
        // returns nothing (e.g. the order genuinely posted a different day) -
        // proves the union, not an intersection, of the two day-sets.
        when(ticketRepository.countGroupedByDayForEvent(eventId)).thenReturn(List.<Object[]>of(new Object[]{day1, 1L}));
        when(orderRepository.sumTotalGroupedByDayForOrderIds(List.of(orderId))).thenReturn(List.<Object[]>of(new Object[]{day2, 1000L}));

        EventAnalyticsResponse response = service.getEventAnalytics(eventId);

        assertThat(response.salesOverTime()).hasSize(1);
        assertThat(response.salesOverTime().get(0).date()).isEqualTo(day1);
        assertThat(response.salesOverTime().get(0).ticketsSold()).isEqualTo(1);
        assertThat(response.salesOverTime().get(0).revenue().amount()).isEqualTo(0L);
    }

    // ---- getPlatformAnalytics ----

    @Test
    void getPlatformAnalytics_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.getPlatformAnalytics())
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getPlatformAnalytics_computesMetricsFromRepositories() {
        when(orderRepository.sumTotalAmount()).thenReturn(9_999_00L);
        when(orderRepository.findDistinctCurrencies()).thenReturn(List.of("USD"));
        when(organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.APPROVED)).thenReturn(7L);
        when(eventRepository.countByDeletedAtIsNull()).thenReturn(123L);

        PlatformAnalyticsResponse response = service.getPlatformAnalytics();

        assertThat(response.totalGmv().amount()).isEqualTo(9_999_00L);
        assertThat(response.totalGmv().currency()).isEqualTo("USD");
        assertThat(response.activeOrganizers()).isEqualTo(7L);
        assertThat(response.eventVolume()).isEqualTo(123L);
    }

    @Test
    void getPlatformAnalytics_noOrdersYet_defaultsCurrencyToUsd() {
        when(orderRepository.sumTotalAmount()).thenReturn(0L);
        when(orderRepository.findDistinctCurrencies()).thenReturn(List.of());
        when(organizationRepository.countByStatusAndDeletedAtIsNull(OrganizationStatus.APPROVED)).thenReturn(0L);
        when(eventRepository.countByDeletedAtIsNull()).thenReturn(0L);

        PlatformAnalyticsResponse response = service.getPlatformAnalytics();

        assertThat(response.totalGmv().currency()).isEqualTo("USD");
    }

    // ---- audience and sales patterns ----

    private static List<EventInsightsRepository.AudienceBuyer> buyers(String group, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> new EventInsightsRepository.AudienceBuyer(group, 2)).toList();
    }

    @Test
    void audience_aSingleSmallGroup_hidesTheNextSmallestToo_soItCannotBeWorkedOutFromTheTotal() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(true);
        List<EventInsightsRepository.AudienceBuyer> all = new java.util.ArrayList<>();
        all.addAll(buyers("18-24", 3));
        all.addAll(buyers("25-34", 6));
        all.addAll(buyers("35-44", 9));
        all.addAll(buyers(null, 2));
        when(insightsRepository.audience(eventId)).thenReturn(all);

        var audience = service.getEventAudience(eventId);

        assertThat(audience.buyers()).isEqualTo(20);
        assertThat(audience.unknownAge()).isEqualTo(2);
        assertThat(audience.ageGroups()).extracting(EventAudienceResponse.AgeGroup::label)
                .containsExactly("Under 18", "18-24", "25-34", "35-44", "45-54", "55+");
        assertThat(audience.ageGroups().get(0).buyers()).isZero();   // empty groups identify nobody
        assertThat(audience.ageGroups().get(1).buyers()).isNull();   // 3: fewer than 5
        assertThat(audience.ageGroups().get(2).buyers()).isNull();   // 6, hidden too: else 20 - 2 - 9 = 11 - 6 would give the 3 away
        assertThat(audience.ageGroups().get(2).tickets()).isNull();
        assertThat(audience.ageGroups().get(3).buyers()).isEqualTo(9);
        assertThat(audience.ageGroups().get(3).tickets()).isEqualTo(18);
    }

    @Test
    void salesPatterns_fillEveryDayUpToToday_whenTheEventHasNotEnded() {
        Event event = event();
        event.setTimezone("UTC");
        event.setEndAt(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(accessGuard.isAdmin()).thenReturn(true);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        when(insightsRepository.salesSlots(eventId, "UTC")).thenReturn(List.of(
                new EventInsightsRepository.SalesSlot(today.minusDays(2).toString(), 3, 9, 4, 2, 1)));

        var patterns = service.getEventSalesPatterns(eventId);

        assertThat(patterns.daily()).extracting(EventSalesPatternsResponse.Day::tickets).containsExactly(4L, 0L, 0L);
        assertThat(patterns.daily().get(2).day()).isEqualTo(today);
        assertThat(patterns.byWeekday()).containsExactly(0, 0, 4, 0, 0, 0, 0);
        assertThat(patterns.byHour()[9]).isEqualTo(4);
        assertThat(patterns.orders()).isEqualTo(2);
        assertThat(patterns.promoOrders()).isEqualTo(1);
    }

    @Test
    void salesPatterns_withNoSales_haveNoDays() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event()));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(insightsRepository.salesSlots(eventId, "UTC")).thenReturn(List.of());

        var patterns = service.getEventSalesPatterns(eventId);

        assertThat(patterns.timezone()).isEqualTo("UTC");   // no zone recorded falls back to UTC
        assertThat(patterns.daily()).isEmpty();
        assertThat(patterns.byHour()).hasSize(24);
    }
}
