package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    public OrderResponse getOrder(UUID orderId) {
        Order order = getOrThrow(orderId);
        requireVisibility(order);
        return toResponse(order);
    }

    @Override
    public List<TicketResponse> getOrderTickets(UUID orderId) {
        Order order = getOrThrow(orderId);
        requireVisibility(order);
        return ticketRepository.findByOrderId(orderId).stream().map(TicketResponse::from).toList();
    }

    @Override
    public Page<OrderResponse> listMyOrders(Pageable pageable) {
        UUID callerId = accessGuard.currentUserId();
        return orderRepository.findByBuyerId(callerId, pageable).map(this::toResponse);
    }

    @Override
    public Page<OrderResponse> listEventOrders(UUID eventId, Pageable pageable) {
        Event event = getEventOrThrow(eventId);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        List<UUID> orderIds = ticketRepository.findDistinctOrderIdsByEventId(eventId);
        if (orderIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return orderRepository.findByIdIn(orderIds, pageable).map(this::toResponse);
    }

    private OrderResponse toResponse(Order order) {
        List<Ticket> tickets = ticketRepository.findByOrderId(order.getId());
        return OrderResponse.from(order, tickets);
    }

    private Order getOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /**
     * BR-CART-004: the buyer, the order's event organizer/owner, or an
     * admin. Resolved via any one of the order's tickets (Order has no
     * eventId column of its own - confirmed decision #3). If an order has
     * zero tickets (shouldn't happen - every checkout now issues at least
     * one per BR-TICKET-001 - but handled defensively), visibility falls
     * back to buyer-or-admin only.
     */
    private void requireVisibility(Order order) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        if (order.getBuyerId().equals(callerId)) {
            return;
        }

        Optional<Ticket> anyTicket = ticketRepository.findFirstByOrderId(order.getId());
        if (anyTicket.isEmpty()) {
            throw new ForbiddenException("Only the order's buyer or an admin may view this order");
        }
        Event event = getEventOrThrow(anyTicket.get().getEventId());
        if (!isOwnerOrOrganizer(callerId, event.getOrganizationId())) {
            throw new ForbiddenException("Only the order's buyer, the event's organizer/owner, or an admin may view this order");
        }
    }

    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        if (!isOwnerOrOrganizer(callerId, organizationId)) {
            throw new ForbiddenException("Only the organization's owner, organizer, or an admin may view this event's orders");
        }
    }

    private boolean isOwnerOrOrganizer(UUID userId, UUID organizationId) {
        return accessGuard.hasRole(userId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(userId, organizationId, OrganizationRole.ORGANIZER);
    }
}
