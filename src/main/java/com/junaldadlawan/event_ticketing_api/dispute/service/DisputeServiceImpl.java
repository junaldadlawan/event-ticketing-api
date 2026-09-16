package com.junaldadlawan.event_ticketing_api.dispute.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
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
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * BR-ADMIN-003. Every read-then-write here is a simple single-row update
 * (unlike {@code RefundServiceImpl}'s money math) - no pessimistic locking
 * precedent is needed for a dispute's own state transitions.
 */
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private static final Set<DisputeStatus> TERMINAL_STATUSES = EnumSet.of(DisputeStatus.RESOLVED, DisputeStatus.DISMISSED);

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final OrganizationAccessGuard accessGuard;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Override
    public DisputeResponse create(DisputeCreateRequest request) {
        UUID callerId = accessGuard.currentUserId();

        if (request.orderId() == null && request.ticketId() == null) {
            throw new BadRequestException("At least one of orderId or ticketId must be provided");
        }

        if (request.orderId() != null) {
            Order order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order " + request.orderId() + " not found"));
            if (!accessGuard.isAdmin() && !order.getBuyerId().equals(callerId)) {
                throw new ForbiddenException("Only the order's buyer or an admin may raise a dispute against it");
            }
        }

        if (request.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket " + request.ticketId() + " not found"));
            if (!accessGuard.isAdmin() && !ticket.getOwnerId().equals(callerId)) {
                throw new ForbiddenException("Only the ticket's owner or an admin may raise a dispute against it");
            }
        }

        Dispute dispute = Dispute.builder()
                .orderId(request.orderId())
                .ticketId(request.ticketId())
                .raisedBy(callerId)
                .status(DisputeStatus.OPEN)
                .reason(request.reason())
                .build();
        return DisputeResponse.from(disputeRepository.save(dispute));
    }

    @Override
    public Page<DisputeResponse> list(DisputeStatus statusFilter, Pageable pageable) {
        accessGuard.requireAdmin();
        Page<Dispute> page = statusFilter != null
                ? disputeRepository.findByStatus(statusFilter, pageable)
                : disputeRepository.findAll(pageable);
        return page.map(DisputeResponse::from);
    }

    @Override
    public DisputeResponse get(UUID disputeId) {
        Dispute dispute = getOrThrow(disputeId);
        if (!accessGuard.isAdmin() && !dispute.getRaisedBy().equals(accessGuard.currentUserId())) {
            throw new ForbiddenException("Only the user who raised this dispute or an admin may view it");
        }
        return DisputeResponse.from(dispute);
    }

    @Override
    public DisputeResponse update(UUID disputeId, DisputeUpdateRequest request) {
        accessGuard.requireAdmin();
        Dispute dispute = getOrThrow(disputeId);

        if (TERMINAL_STATUSES.contains(dispute.getStatus())) {
            throw new ConflictException("This dispute has already been resolved");
        }

        if (request.status() != null) {
            dispute.setStatus(request.status());
        }
        if (request.resolution() != null) {
            dispute.setResolution(request.resolution());
        }
        dispute.setUpdatedBy(accessGuard.currentUserId());
        Dispute saved = disputeRepository.save(dispute);

        // BR-NOTIFY-001-equivalent: tell the raiser once their dispute reaches
        // a terminal state. Best-effort, never throws (NFR 5.2).
        if (TERMINAL_STATUSES.contains(saved.getStatus())) {
            notificationService.notify(saved.getRaisedBy(), NotificationType.DISPUTE_RESOLVED, "Dispute", saved.getId());
            // BR-NFR-005 (Phase 13): sensitive-action audit trail, same
            // terminal-status condition as the notification above. Never throws.
            String auditAction = saved.getStatus() == DisputeStatus.DISMISSED ? "dispute.dismissed" : "dispute.resolved";
            auditLogService.record(saved.getUpdatedBy(), auditAction, "Dispute", saved.getId());
        }

        return DisputeResponse.from(saved);
    }

    private Dispute getOrThrow(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute " + disputeId + " not found"));
    }
}
