package com.junaldadlawan.event_ticketing_api.refundpolicy.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyResponse;
import com.junaldadlawan.event_ticketing_api.refundpolicy.dto.RefundPolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link RefundPolicyServiceImpl} (no Spring context)
 * — mirrors {@code ResalePolicyServiceImplTest}'s owner/organizer/admin
 * authorization style. Covers the synthesized {@code NO_REFUNDS} default,
 * the wider {@code get} visibility (organizer/owner, admin, OR a buyer with
 * any ticket on the event — broader than {@code update}'s owner/organizer/
 * admin-only gate), and the upsert + race-retry semantics.
 */
@ExtendWith(MockitoExtension.class)
class RefundPolicyServiceImplTest {

    @Mock
    private RefundPolicyRepository refundPolicyRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private RefundPolicyServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new RefundPolicyServiceImpl(refundPolicyRepository, eventRepository, ticketRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert")
                .description("desc")
                .category("music")
                .status(EventStatus.PUBLISHED)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private RefundPolicyUpdateRequest request(RefundRuleType ruleType, Integer days, String terms) {
        return new RefundPolicyUpdateRequest(ruleType, days, terms);
    }

    // ---- get() ----

    @Test
    void get_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(eventId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(refundPolicyRepository);
    }

    @Test
    void get_noPolicyRowYet_returnsSynthesizedNoRefundsDefault() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        RefundPolicyResponse response = service.get(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.ruleType()).isEqualTo(RefundRuleType.NO_REFUNDS);
        assertThat(response.daysBeforeEvent()).isNull();
        assertThat(response.customTerms()).isNull();
    }

    @Test
    void get_admin_bypassesRoleAndTicketLookup() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        RefundPolicyResponse response = service.get(eventId);

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.NO_REFUNDS);
        verify(accessGuard, never()).currentUserId();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void get_buyerWithOrderOnEvent_isPermitted() {
        UUID eventId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(buyerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);
        when(ticketRepository.existsByEventIdAndOwnerId(eventId, buyerId)).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        RefundPolicyResponse response = service.get(eventId);

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.NO_REFUNDS);
    }

    @Test
    void get_strangerWithNoRoleAndNoTicket_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);
        when(ticketRepository.existsByEventIdAndOwnerId(eventId, strangerId)).thenReturn(false);

        assertThatThrownBy(() -> service.get(eventId)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(refundPolicyRepository);
    }

    @Test
    void get_existingPolicyRow_mapsFields() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        RefundPolicy policy = RefundPolicy.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .ruleType(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS)
                .daysBeforeEvent(7)
                .build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));

        RefundPolicyResponse response = service.get(eventId);

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS);
        assertThat(response.daysBeforeEvent()).isEqualTo(7);
    }

    // ---- update() authorization ----

    @Test
    void update_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(eventId, request(RefundRuleType.NO_REFUNDS, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(refundPolicyRepository, accessGuard);
    }

    @Test
    void update_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(eventId, request(RefundRuleType.NO_REFUNDS, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(refundPolicyRepository);
    }

    @Test
    void update_crossOrgOwner_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(eventId, request(RefundRuleType.NO_REFUNDS, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_admin_bypassesOrgRoleCheck_withNoCurrentUserIdOrHasRoleCallAtAll() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(refundPolicyRepository.saveAndFlush(any(RefundPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundPolicyResponse response = service.update(eventId, request(RefundRuleType.NO_REFUNDS, null, null));

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.NO_REFUNDS);
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void update_organizer_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(refundPolicyRepository.saveAndFlush(any(RefundPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundPolicyResponse response = service.update(eventId, request(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS, 14, null));

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS);
        assertThat(response.daysBeforeEvent()).isEqualTo(14);
    }

    // ---- update() upsert semantics ----

    @Test
    void update_noExistingRow_createsNewPolicyForTheEvent() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        ArgumentCaptor<RefundPolicy> captor = ArgumentCaptor.forClass(RefundPolicy.class);
        when(refundPolicyRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.update(eventId, request(RefundRuleType.CUSTOM, null, "Store credit only"));

        RefundPolicy saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRuleType()).isEqualTo(RefundRuleType.CUSTOM);
        assertThat(saved.getCustomTerms()).isEqualTo("Store credit only");
    }

    @Test
    void update_existingRow_upsertsInPlace_ratherThanCreatingASecondRow() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID existingPolicyId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        RefundPolicy existing = RefundPolicy.builder()
                .id(existingPolicyId).eventId(eventId).ruleType(RefundRuleType.CUSTOM).customTerms("old terms")
                .build();
        when(refundPolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(refundPolicyRepository.save(any(RefundPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        // Switching rule types and clearing customTerms (omitted from the request).
        RefundPolicyResponse response = service.update(eventId, request(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS, 30, null));

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.REFUNDABLE_UNTIL_N_DAYS);
        assertThat(response.daysBeforeEvent()).isEqualTo(30);
        assertThat(response.customTerms()).isNull();
        verify(refundPolicyRepository).save(existing);
        assertThat(existing.getId()).isEqualTo(existingPolicyId); // same row, not a duplicate
        assertThat(existing.getCustomTerms()).isNull();
    }

    @Test
    void update_firstInsertRaceLostAtDbLevel_dataIntegrityViolation_retriesAgainstWinner() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        // First lookup misses (race with a concurrent first-time PATCH).
        RefundPolicy winner = RefundPolicy.builder().id(winnerId).eventId(eventId).ruleType(RefundRuleType.NO_REFUNDS).build();
        when(refundPolicyRepository.findByEventId(eventId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(refundPolicyRepository.saveAndFlush(any(RefundPolicy.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        when(refundPolicyRepository.save(any(RefundPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundPolicyResponse response = service.update(eventId, request(RefundRuleType.CUSTOM, null, "backstop win"));

        assertThat(response.ruleType()).isEqualTo(RefundRuleType.CUSTOM);
        assertThat(response.customTerms()).isEqualTo("backstop win");
        verify(refundPolicyRepository).save(winner);
    }
}
