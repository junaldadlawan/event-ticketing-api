package com.junaldadlawan.event_ticketing_api.dispute.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeCreateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeResponse;
import com.junaldadlawan.event_ticketing_api.dispute.dto.DisputeUpdateRequest;
import com.junaldadlawan.event_ticketing_api.dispute.entity.Dispute;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import com.junaldadlawan.event_ticketing_api.dispute.repository.DisputeRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link DisputeServiceImpl} (no Spring context) -
 * mirrors {@code RefundServiceImplTest}'s style. Covers BR-ADMIN-003:
 * the orderId/ticketId presence guard, buyer/owner-or-admin raise
 * authorization, admin-only list/update, raiser-or-admin get visibility,
 * the terminal-state (RESOLVED/DISMISSED) guard on update, and the
 * resolved/dismissed notification trigger.
 */
@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    private DisputeServiceImpl disputeService;

    @BeforeEach
    void setUp() {
        disputeService = new DisputeServiceImpl(disputeRepository, orderRepository, ticketRepository, accessGuard, notificationService, auditLogService);
    }

    private Order order(UUID id, UUID buyerId) {
        return Order.builder().id(id).buyerId(buyerId).payeeType(PayeeType.ORGANIZATION).payeeId(UUID.randomUUID())
                .total(Money.builder().amount(1000L).currency("USD").build()).build();
    }

    private Ticket ticket(UUID id, UUID ownerId) {
        return Ticket.builder().id(id).eventId(UUID.randomUUID()).ticketTypeId(UUID.randomUUID())
                .orderId(UUID.randomUUID()).ownerId(ownerId).ticketNumber("A-000001").credential("cred")
                .status(TicketStatus.VALID).build();
    }

    private Dispute dispute(UUID id, UUID raisedBy, DisputeStatus status) {
        return Dispute.builder().id(id).raisedBy(raisedBy).status(status).reason("didn't receive tickets").build();
    }

    // ---- create() ----

    @Test
    void create_neitherOrderNorTicket_throwsBadRequest() {
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);

        assertThatThrownBy(() -> disputeService.create(new DisputeCreateRequest(null, null, "reason")))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(disputeRepository);
    }

    @Test
    void create_orderBuyer_succeeds() {
        UUID callerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, callerId)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.create(new DisputeCreateRequest(orderId, null, "not delivered"));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.raisedBy()).isEqualTo(callerId);
        assertThat(result.status()).isEqualTo(DisputeStatus.OPEN);
    }

    @Test
    void create_orderNonBuyerNonAdmin_throwsForbidden() {
        UUID callerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, UUID.randomUUID())));
        when(accessGuard.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> disputeService.create(new DisputeCreateRequest(orderId, null, "reason")))
                .isInstanceOf(ForbiddenException.class);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void create_orderAdmin_succeedsEvenThoughNotBuyer() {
        UUID callerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, UUID.randomUUID())));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.create(new DisputeCreateRequest(orderId, null, "reason"));

        assertThat(result.raisedBy()).isEqualTo(callerId);
    }

    @Test
    void create_unknownOrder_throwsResourceNotFound() {
        UUID callerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.create(new DisputeCreateRequest(orderId, null, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_ticketOwner_succeeds() {
        UUID callerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, callerId)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.create(new DisputeCreateRequest(null, ticketId, "counterfeit"));

        assertThat(result.ticketId()).isEqualTo(ticketId);
    }

    @Test
    void create_ticketNonOwnerNonAdmin_throwsForbidden() {
        UUID callerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, UUID.randomUUID())));
        when(accessGuard.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> disputeService.create(new DisputeCreateRequest(null, ticketId, "reason")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_unknownTicket_throwsResourceNotFound() {
        UUID callerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.create(new DisputeCreateRequest(null, ticketId, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- list() ----

    @Test
    void list_admin_withStatusFilter_delegatesToFindByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Dispute> page = new PageImpl<>(java.util.List.of(dispute(UUID.randomUUID(), UUID.randomUUID(), DisputeStatus.OPEN)));
        when(disputeRepository.findByStatus(DisputeStatus.OPEN, pageable)).thenReturn(page);

        Page<DisputeResponse> result = disputeService.list(DisputeStatus.OPEN, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(disputeRepository, never()).findAll(pageable);
    }

    @Test
    void list_admin_noStatusFilter_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(disputeRepository.findAll(pageable)).thenReturn(Page.empty());

        disputeService.list(null, pageable);

        verify(disputeRepository).findAll(pageable);
        verify(disputeRepository, never()).findByStatus(any(), any());
    }

    @Test
    void list_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> disputeService.list(null, pageable)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(disputeRepository);
    }

    // ---- get() ----

    @Test
    void get_raiser_succeeds() {
        UUID disputeId = UUID.randomUUID();
        UUID raiserId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, raiserId, DisputeStatus.OPEN)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(raiserId);

        DisputeResponse result = disputeService.get(disputeId);

        assertThat(result.raisedBy()).isEqualTo(raiserId);
    }

    @Test
    void get_admin_succeedsEvenThoughNotRaiser() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, UUID.randomUUID(), DisputeStatus.OPEN)));
        when(accessGuard.isAdmin()).thenReturn(true);

        DisputeResponse result = disputeService.get(disputeId);

        assertThat(result.id()).isEqualTo(disputeId);
        verify(accessGuard, never()).currentUserId();
    }

    @Test
    void get_stranger_throwsForbidden() {
        UUID disputeId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, UUID.randomUUID(), DisputeStatus.OPEN)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);

        assertThatThrownBy(() -> disputeService.get(disputeId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void get_unknownDispute_throwsResourceNotFound() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.get(disputeId)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- update() ----

    @Test
    void update_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> disputeService.update(UUID.randomUUID(), new DisputeUpdateRequest(DisputeStatus.RESOLVED, "done")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(disputeRepository);
    }

    @Test
    void update_unknownDispute_throwsResourceNotFound() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.RESOLVED, "done")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_alreadyResolved_throwsConflict() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, UUID.randomUUID(), DisputeStatus.RESOLVED)));

        assertThatThrownBy(() -> disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.DISMISSED, "done")))
                .isInstanceOf(ConflictException.class);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void update_alreadyDismissed_throwsConflict() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, UUID.randomUUID(), DisputeStatus.DISMISSED)));

        assertThatThrownBy(() -> disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.RESOLVED, "done")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_toInvestigating_doesNotNotify() {
        UUID disputeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID raiserId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, raiserId, DisputeStatus.OPEN)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.INVESTIGATING, null));

        assertThat(result.status()).isEqualTo(DisputeStatus.INVESTIGATING);
        assertThat(result.updatedBy()).isEqualTo(adminId);
        verifyNoInteractions(notificationService);
    }

    @Test
    void update_toResolved_setsResolutionAndNotifiesRaiser() {
        UUID disputeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID raiserId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, raiserId, DisputeStatus.OPEN)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.RESOLVED, "refund issued"));

        assertThat(result.status()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(result.resolution()).isEqualTo("refund issued");
        verify(notificationService, times(1)).notify(eq(raiserId), eq(NotificationType.DISPUTE_RESOLVED), eq("Dispute"), eq(disputeId));
    }

    @Test
    void update_toDismissed_notifiesRaiser() {
        UUID disputeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID raiserId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, raiserId, DisputeStatus.INVESTIGATING)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        disputeService.update(disputeId, new DisputeUpdateRequest(DisputeStatus.DISMISSED, "no evidence"));

        verify(notificationService).notify(eq(raiserId), eq(NotificationType.DISPUTE_RESOLVED), any(), any());
    }

    @Test
    void update_resolutionOnly_leavesStatusUnchanged() {
        UUID disputeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(adminId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute(disputeId, UUID.randomUUID(), DisputeStatus.INVESTIGATING)));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse result = disputeService.update(disputeId, new DisputeUpdateRequest(null, "still looking into it"));

        assertThat(result.status()).isEqualTo(DisputeStatus.INVESTIGATING);
        assertThat(result.resolution()).isEqualTo("still looking into it");
    }
}
