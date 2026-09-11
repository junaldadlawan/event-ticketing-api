package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Order/ticket retrieval (Phase 6a) - separate from {@link CheckoutService},
 * which only handles the checkout write path. Deliberate deviation from the
 * rest of the codebase's Service-returns-entity / Controller-maps-to-DTO
 * convention, same reasoning as {@code CartService}: an Order's response
 * shape now requires cross-referencing its issued Tickets (Order has no
 * {@code tickets} relationship of its own - see {@code Ticket}'s javadoc),
 * so assembling it is a service-layer read-model concern.
 */
public interface OrderService {

    /**
     * {@code GET /orders/{orderId}} (BR-CART-004) - visible to the buyer,
     * the order's event organizer/owner, or an admin.
     */
    OrderResponse getOrder(UUID orderId);

    /**
     * {@code GET /orders/{orderId}/tickets} - same visibility as {@link
     * #getOrder}.
     */
    List<TicketResponse> getOrderTickets(UUID orderId);

    /** {@code GET /users/me/orders} - orders placed by the caller. */
    Page<OrderResponse> listMyOrders(Pageable pageable);

    /**
     * {@code GET /events/{eventId}/orders} - owning organizer/owner or admin
     * only (no buyer bypass - this endpoint is organizer-scoped).
     */
    Page<OrderResponse> listEventOrders(UUID eventId, Pageable pageable);
}
