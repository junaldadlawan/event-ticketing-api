package com.junaldadlawan.event_ticketing_api.tickettransfer.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResaleListing;
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
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link TicketTransferServiceImpl} (no Spring
 * context) — mirrors {@code CheckoutServiceImplTest}'s style. Covers {@code
 * transfer}'s authorization/status/recipient-validation branches
 * (BR-TRANSFER-001), {@code listTransfers}'s delegation to {@link
 * TicketAccessGuard}, and the shared {@code recordTransfer} primitive's
 * credential-invalidation mechanics (BR-TRANSFER-005) in isolation from the
 * HTTP-facing {@code transfer} method.
 */
@ExtendWith(MockitoExtension.class)
class TicketTransferServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketTransferRepository ticketTransferRepository;
    @Mock
    private TicketAccessGuard ticketAccessGuard;
    @Mock
    private TicketCredentialService ticketCredentialService;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResaleListingRepository resaleListingRepository;

    private TicketTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketTransferServiceImpl(
                ticketRepository, ticketTransferRepository, ticketAccessGuard,
                ticketCredentialService, accessGuard, userRepository, resaleListingRepository);
    }

    private Ticket ticket(UUID id, UUID ownerId, TicketStatus status, int credentialVersion) {
        return Ticket.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .seatId(null)
                .ownerId(ownerId)
                .ticketNumber("ABC-A2B3C4")
                .credential("old-credential")
                .credentialVersion(credentialVersion)
                .status(status)
                .build();
    }

    private User user(UUID id) {
        return User.builder().id(id).email("u-" + id + "@test.local").role(Role.CUSTOMER).build();
    }

    // ---- transfer() ----

    @Test
    void transfer_unknownTicket_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(ticketId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketTransferRepository, userRepository);
    }

    @Test
    void transfer_nonOwningCaller_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);

        assertThatThrownBy(() -> service.transfer(ticketId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userRepository, ticketTransferRepository);
    }

    @Test
    void transfer_ticketNotValid_throwsConflict() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.USED, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.transfer(ticketId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void transfer_recipientIsCurrentOwner_throwsBadRequest() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.transfer(ticketId, ownerId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void transfer_recipientNotRegistered_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(userRepository.findById(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(ticketId, recipientId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketTransferRepository);
    }

    @Test
    void transfer_recipientSoftDeleted_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        User deletedRecipient = user(recipientId);
        deletedRecipient.markDeleted();
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(deletedRecipient));

        assertThatThrownBy(() -> service.transfer(ticketId, recipientId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketTransferRepository);
    }

    @Test
    void transfer_validRequest_bumpsCredentialVersion_regeneratesCredential_reassignsOwner_andRecordsAuditRow() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, ownerId, TicketStatus.VALID, 0);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId)));
        when(ticketCredentialService.generate(ticketId, 1)).thenReturn("new-credential-v1");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = service.transfer(ticketId, recipientId);

        assertThat(result.getOwnerId()).isEqualTo(recipientId);
        assertThat(result.getCredentialVersion()).isEqualTo(1);
        assertThat(result.getCredential()).isEqualTo("new-credential-v1");
        // Untouched fields (per dispatch's decision #2).
        assertThat(result.getId()).isEqualTo(ticketId);
        assertThat(result.getEventId()).isEqualTo(ticket.getEventId());
        assertThat(result.getTicketTypeId()).isEqualTo(ticket.getTicketTypeId());
        assertThat(result.getSeatId()).isEqualTo(ticket.getSeatId());
        assertThat(result.getTicketNumber()).isEqualTo(ticket.getTicketNumber());
        assertThat(result.getStatus()).isEqualTo(TicketStatus.VALID);

        ArgumentCaptor<TicketTransfer> transferCaptor = ArgumentCaptor.forClass(TicketTransfer.class);
        verify(ticketTransferRepository).save(transferCaptor.capture());
        TicketTransfer recorded = transferCaptor.getValue();
        assertThat(recorded.getTicketId()).isEqualTo(ticketId);
        assertThat(recorded.getFromUserId()).isEqualTo(ownerId);
        assertThat(recorded.getToUserId()).isEqualTo(recipientId);
        assertThat(recorded.getSource()).isEqualTo(TransferSource.DIRECT_TRANSFER);
    }

    /**
     * Code-reviewer CRITICAL: a resale listing must never outlive the
     * ownership state it was created against - without this, a seller could
     * list a ticket, directly transfer it away, and a later resale buyer
     * would pay the ORIGINAL seller for a ticket a different person now owns.
     */
    @Test
    void transfer_ticketHasActiveResaleListing_autoCancelsItBeforeReassigningOwner() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId)));
        ResaleListing activeListing = ResaleListing.builder()
                .id(UUID.randomUUID()).ticketId(ticketId).sellerId(ownerId)
                .status(ResaleListingStatus.ACTIVE).build();
        when(resaleListingRepository.findByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing));
        when(ticketCredentialService.generate(ticketId, 1)).thenReturn("new-credential-v1");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(ticketId, recipientId);

        ArgumentCaptor<ResaleListing> listingCaptor = ArgumentCaptor.forClass(ResaleListing.class);
        verify(resaleListingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getStatus()).isEqualTo(ResaleListingStatus.CANCELLED);
        assertThat(listingCaptor.getValue().getResolvedAt()).isNotNull();
    }

    @Test
    void transfer_ticketHasNoActiveResaleListing_doesNotTouchResaleListingRepository() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, TicketStatus.VALID, 0)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId)));
        when(resaleListingRepository.findByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(Optional.empty());
        when(ticketCredentialService.generate(ticketId, 1)).thenReturn("new-credential-v1");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(ticketId, recipientId);

        verify(resaleListingRepository, never()).save(any());
    }

    // ---- listTransfers() ----

    @Test
    void listTransfers_unknownTicket_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listTransfers(ticketId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketTransferRepository);
    }

    @Test
    void listTransfers_accessGuardApproves_returnsHistoryOldestFirst() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, UUID.randomUUID(), TicketStatus.VALID, 2);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        List<TicketTransfer> history = List.of(
                TicketTransfer.builder().id(UUID.randomUUID()).ticketId(ticketId).source(TransferSource.DIRECT_TRANSFER).build());
        when(ticketTransferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId)).thenReturn(history);

        List<TicketTransfer> result = service.listTransfers(ticketId);

        assertThat(result).isEqualTo(history);
        verify(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);
    }

    @Test
    void listTransfers_accessGuardRejects_propagatesForbidden() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, UUID.randomUUID(), TicketStatus.VALID, 0);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        doThrow(new ForbiddenException("nope")).when(ticketAccessGuard).requireOwnerBuyerOrOrganizerOrAdmin(ticket);

        assertThatThrownBy(() -> service.listTransfers(ticketId)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(ticketTransferRepository);
    }

    // ---- recordTransfer() shared primitive, exercised directly (as the resale-purchase path calls it) ----

    @Test
    void recordTransfer_resaleSource_bumpsVersionFromWhateverItStartedAt_andTagsSourceCorrectly() {
        UUID ticketId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, sellerId, TicketStatus.VALID, 3); // e.g. already transferred a few times before
        when(ticketCredentialService.generate(ticketId, 4)).thenReturn("resale-credential-v4");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = service.recordTransfer(ticket, buyerId, TransferSource.RESALE);

        assertThat(result.getOwnerId()).isEqualTo(buyerId);
        assertThat(result.getCredentialVersion()).isEqualTo(4);
        assertThat(result.getCredential()).isEqualTo("resale-credential-v4");

        ArgumentCaptor<TicketTransfer> transferCaptor = ArgumentCaptor.forClass(TicketTransfer.class);
        verify(ticketTransferRepository).save(transferCaptor.capture());
        assertThat(transferCaptor.getValue().getSource()).isEqualTo(TransferSource.RESALE);
        assertThat(transferCaptor.getValue().getFromUserId()).isEqualTo(sellerId);
        assertThat(transferCaptor.getValue().getToUserId()).isEqualTo(buyerId);
    }

    @Test
    void recordTransfer_producesDifferentCredentialThanBefore_forTheSameTicketId() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = ticket(ticketId, UUID.randomUUID(), TicketStatus.VALID, 0);
        String originalCredential = ticket.getCredential();
        when(ticketCredentialService.generate(eq(ticketId), eq(1))).thenReturn("distinctly-different-credential");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = service.recordTransfer(ticket, UUID.randomUUID(), TransferSource.DIRECT_TRANSFER);

        assertThat(result.getCredential()).isNotEqualTo(originalCredential);
        assertThat(result.getId()).isEqualTo(ticketId); // same ticket row, per BR-TRANSFER-005
    }
}
