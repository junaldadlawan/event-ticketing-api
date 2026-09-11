package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.service.OrderService;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Order/ticket retrieval endpoints (Phase 6a). Kept in the existing {@code
 * order/} module (not the new {@code ticket/} module) to keep Order-shaped
 * concerns together, per the dispatch's own guidance - only Ticket-shaped
 * reads (e.g. {@code GET /tickets/{ticketId}}) live in {@code ticket/}.
 * <p>
 * No class-level {@code @RequestMapping}: the four endpoints here span three
 * different top-level path prefixes ({@code /users/me/orders}, {@code
 * /orders/**}, {@code /events/{eventId}/orders}) rather than sharing one.
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/api/v1/users/me/orders")
    public PageResponse<OrderResponse> listMyOrders(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderService.listMyOrders(pageable));
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping("/api/v1/orders/{orderId}/tickets")
    public List<TicketResponse> getOrderTickets(@PathVariable UUID orderId) {
        return orderService.getOrderTickets(orderId);
    }

    @GetMapping("/api/v1/events/{eventId}/orders")
    public PageResponse<OrderResponse> listEventOrders(@PathVariable UUID eventId,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(orderService.listEventOrders(eventId, pageable));
    }
}
