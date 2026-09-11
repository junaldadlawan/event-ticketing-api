package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link TicketServiceImpl} (no Spring context) —
 * mirrors {@code TicketTypeServiceImplTest}'s style. {@code
 * TicketServiceImpl} itself only resolves the ticket by id and delegates the
 * BR-CART-004-equivalent visibility rule to {@link TicketAccessGuard} (Phase
 * 6b extraction) — the rule's own branch coverage lives in {@link
 * TicketAccessGuardTest}.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAccessGuard ticketAccessGuard;

    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketServiceImpl(ticketRepository, ticketAccessGuard);
    }

    private Ticket ticket(UUID id, UUID ownerId) {
        return Ticket.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .seatId(null)
                .ownerId(ownerId)
                .ticketNumber("ABC-A2B3C4")
                .credential("irrelevant-for-this-test")
                .status(TicketStatus.VALID)
                .build();
    }

    @Test
    void getTicket_unknownTicket_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicket(ticketId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketAccessGuard);
    }

    @Test
    void getTicket_found_delegatesVisibilityCheckToAccessGuardAndReturnsTicket() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, UUID.randomUUID());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        doNothing().when(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);

        Ticket result = service.getTicket(ticketId);

        assertThat(result.getId()).isEqualTo(ticketId);
        verify(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);
    }

    @Test
    void getTicket_accessGuardRejects_propagatesForbidden() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, UUID.randomUUID());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        doThrow(new ForbiddenException("nope")).when(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);

        assertThatThrownBy(() -> service.getTicket(ticketId)).isInstanceOf(ForbiddenException.class);
    }
}
