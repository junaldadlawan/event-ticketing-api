package com.junaldadlawan.event_ticketing_api.tickettransfer.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResaleListingRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;
import com.junaldadlawan.event_ticketing_api.tickettransfer.repository.TicketTransferRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketTransferServiceImpl implements TicketTransferService {

    private final TicketRepository ticketRepository;
    private final TicketTransferRepository ticketTransferRepository;
    private final TicketAccessGuard ticketAccessGuard;
    private final TicketCredentialService ticketCredentialService;
    private final OrganizationAccessGuard accessGuard;
    private final UserRepository userRepository;
    private final ResaleListingRepository resaleListingRepository;

    @Override
    @Transactional
    public Ticket transfer(UUID ticketId, UUID toUserId) {
        // Locked (not a plain findById) - the same row can otherwise be
        // concurrently mutated by a resale-listing purchase of this same
        // ticket (ResaleListingServiceImpl also locks via findByIdForUpdate).
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));

        UUID callerId = accessGuard.currentUserId();
        if (!ticket.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("Only the ticket's owning buyer may transfer it");
        }
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new ConflictException("Only a valid ticket may be transferred");
        }
        if (toUserId.equals(ticket.getOwnerId())) {
            throw new BadRequestException("Cannot transfer a ticket to its own current owner");
        }
        // BR-TRANSFER-001: recipient must be a registered user.
        User recipient = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + toUserId + " not found"));
        if (recipient.getDeletedAt() != null) {
            throw new ResourceNotFoundException("User " + toUserId + " not found");
        }

        // code-reviewer CRITICAL: a resale listing must never outlive the
        // ownership state it was created against - without this, a seller
        // could list a ticket, directly transfer it away to someone else,
        // and a later resale-listing purchaser would pay the ORIGINAL
        // seller for a ticket a DIFFERENT person now owns, then have the
        // ticket silently reassigned out from under them. Auto-cancel any
        // still-ACTIVE listing on this ticket before the ownership actually
        // changes (at most one can exist - V14's partial unique index).
        resaleListingRepository.findByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)
                .ifPresent(listing -> {
                    listing.setStatus(ResaleListingStatus.CANCELLED);
                    listing.setResolvedAt(Instant.now());
                    resaleListingRepository.save(listing);
                });

        return recordTransfer(ticket, toUserId, TransferSource.DIRECT_TRANSFER);
    }

    @Override
    public List<TicketTransfer> listTransfers(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        // Same BR-CART-004-equivalent visibility as GET /tickets/{id} (Phase 6a).
        ticketAccessGuard.requireOwnerBuyerOrOrganizerOrAdmin(ticket);
        return ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId);
    }

    @Override
    @Transactional
    public Ticket recordTransfer(Ticket ticket, UUID toUserId, TransferSource source) {
        UUID fromUserId = ticket.getOwnerId();

        int newVersion = ticket.getCredentialVersion() + 1;
        ticket.setOwnerId(toUserId);
        ticket.setCredentialVersion(newVersion);
        ticket.setCredential(ticketCredentialService.generate(ticket.getId(), newVersion));
        Ticket saved = ticketRepository.save(ticket);

        TicketTransfer transfer = TicketTransfer.builder()
                .ticketId(ticket.getId())
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .source(source)
                .build();
        ticketTransferRepository.save(transfer);

        return saved;
    }
}
