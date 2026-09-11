package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link OrderServiceImpl} (no Spring context) —
 * mirrors {@code TicketServiceImplTest}/{@code TicketTypeServiceImplTest}'s
 * style. Covers BR-CART-004's visibility scoping for all four retrieval
 * methods, with special attention to {@code listEventOrders}'s deliberately
 * DIFFERENT rule (organizer/owner/admin only — the order's own buyer must
 * NOT get a bypass there unless they're also that event's organizer/admin).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private OrderServiceImpl service;

    private UUID orgId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, ticketRepository, eventRepository, accessGuard);
        orgId = UUID.randomUUID();
        eventId = UUID.randomUUID();
    }

    private Order order(UUID id, UUID buyerId) {
        return Order.builder()
                .id(id)
                .buyerId(buyerId)
                .payeeType(PayeeType.ORGANIZATION)
                .payeeId(orgId)
                .status(OrderStatus.PAID)
                .total(Money.builder().amount(1000L).currency("USD").build())
                .createdBy(buyerId.toString())
                .createdAt(Instant.now())
                .build();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert Night")
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .build();
    }

    private Ticket ticket(UUID orderId, UUID eventId) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .eventId(eventId)
                .ticketTypeId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .ticketNumber("ABC-A2B3C4")
                .credential("irrelevant")
                .status(TicketStatus.VALID)
                .build();
    }

    // ---- getOrder() ----

    @Test
    void getOrder_unknownOrder_throwsResourceNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrder_owningBuyer_succeeds_withoutTouchingTicketOrEventRepositoryForVisibility() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response = service.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
        // Visibility short-circuits on buyer match; findFirstByOrderId (used only
        // for non-buyer visibility resolution) is never called.
        verify(ticketRepository, never()).findFirstByOrderId(any());
    }

    @Test
    void getOrder_admin_succeeds() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response = service.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void getOrder_eventOrganizer_succeeds() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(ticket(orderId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response = service.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
    }

    @Test
    void getOrder_stranger_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(ticket(orderId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getOrder(orderId)).isInstanceOf(ForbiddenException.class);
    }

    /** Key regression class: a DIFFERENT org's organizer, not just a roleless stranger. */
    @Test
    void getOrder_crossOrgOrganizer_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID otherOrgOrganizerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOrganizerId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(ticket(orderId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getOrder(orderId)).isInstanceOf(ForbiddenException.class);
    }

    /** Defensive fallback: an order with zero tickets falls back to buyer-or-admin only. */
    @Test
    void getOrder_nonBuyerNonAdmin_orderHasNoTickets_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(orderId)).isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    // ---- getOrderTickets() ----

    @Test
    void getOrderTickets_owningBuyer_returnsTicketsWithoutCredential() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        Ticket t = ticket(orderId, eventId);
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of(t));

        List<TicketResponse> result = service.getOrderTickets(orderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(t.getId());
    }

    @Test
    void getOrderTickets_stranger_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, buyerId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(ticketRepository.findFirstByOrderId(orderId)).thenReturn(Optional.of(ticket(orderId, eventId)));
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getOrderTickets(orderId)).isInstanceOf(ForbiddenException.class);
    }

    // ---- listMyOrders() ----

    @Test
    void listMyOrders_usesCallerIdAsBuyerId_selfScopedOnly() {
        UUID callerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        Order myOrder = order(UUID.randomUUID(), callerId);
        when(orderRepository.findByBuyerId(callerId, pageable)).thenReturn(new PageImpl<>(List.of(myOrder)));
        when(ticketRepository.findByOrderId(myOrder.getId())).thenReturn(List.of());

        Page<OrderResponse> result = service.listMyOrders(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).buyerId()).isEqualTo(callerId);
        // Never queries by any other buyer id - no cross-user leakage possible from this call shape.
        verify(orderRepository).findByBuyerId(eq(callerId), eq(pageable));
    }

    // ---- listEventOrders() : deliberately NOT buyer-scoped ----

    @Test
    void listEventOrders_unknownEvent_throwsResourceNotFound() {
        UUID unknownEventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(unknownEventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listEventOrders(unknownEventId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listEventOrders_organizer_succeeds() {
        UUID organizerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        UUID orderId = UUID.randomUUID();
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of(orderId));
        Order o = order(orderId, UUID.randomUUID());
        when(orderRepository.findByIdIn(List.of(orderId), pageable)).thenReturn(new PageImpl<>(List.of(o)));
        when(ticketRepository.findByOrderId(orderId)).thenReturn(List.of());

        Page<OrderResponse> result = service.listEventOrders(eventId, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listEventOrders_admin_succeeds() {
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of());

        Page<OrderResponse> result = service.listEventOrders(eventId, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void listEventOrders_noOrdersYet_returnsEmptyPage_withoutQueryingOrderRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(ticketRepository.findDistinctOrderIdsByEventId(eventId)).thenReturn(List.of());

        Page<OrderResponse> result = service.listEventOrders(eventId, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository, never()).findByIdIn(any(), any());
    }

    /**
     * The critical, explicitly-non-buyer-scoped rule: the order's OWN BUYER,
     * with no organizer/owner/admin role on that event's organization, must
     * be forbidden from listing that event's orders — this endpoint is not a
     * "my orders" view, unlike {@code getOrder}/{@code getOrderTickets}.
     */
    @Test
    void listEventOrders_ordersOwnBuyerWithNoOrganizerRole_throwsForbidden() {
        UUID buyerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.listEventOrders(eventId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).findDistinctOrderIdsByEventId(any());
    }

    /** Same cross-org regression class as everywhere else, on this endpoint too. */
    @Test
    void listEventOrders_crossOrgOwner_throwsForbidden() {
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.listEventOrders(eventId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }
}
