package com.junaldadlawan.event_ticketing_api.resalepolicy.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyResponse;
import com.junaldadlawan.event_ticketing_api.resalepolicy.dto.ResalePolicyUpdateRequest;
import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.resalepolicy.repository.ResalePolicyRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ResalePolicyServiceImpl} (no Spring
 * context) — mirrors {@code TicketTemplateServiceImplTest}'s
 * owner/organizer/admin authorization style. Covers BR-TRANSFER-003/004: the
 * synthesized {@code enabled=false} default when no row exists yet, the
 * owner/organizer/admin-only PATCH gate, and the upsert semantics.
 */
@ExtendWith(MockitoExtension.class)
class ResalePolicyServiceImplTest {

    @Mock
    private ResalePolicyRepository resalePolicyRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private ResalePolicyServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new ResalePolicyServiceImpl(resalePolicyRepository, eventRepository, accessGuard);
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

    private ResalePolicyUpdateRequest request(boolean enabled, PriceCapRule rule, MoneyDto fee) {
        return new ResalePolicyUpdateRequest(enabled, rule, fee);
    }

    // ---- get() ----

    @Test
    void get_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(eventId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resalePolicyRepository);
    }

    @Test
    void get_noPolicyRowYet_returnsSynthesizedDisabledDefault() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        ResalePolicyResponse response = service.get(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.enabled()).isFalse();
        assertThat(response.priceCapRule()).isNull();
        assertThat(response.feeAmount()).isNull();
    }

    @Test
    void get_existingPolicyRow_mapsFields() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        ResalePolicy policy = ResalePolicy.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .enabled(true)
                .priceCapRule(PriceCapRule.FACE_VALUE)
                .build();
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));

        ResalePolicyResponse response = service.get(eventId);

        assertThat(response.enabled()).isTrue();
        assertThat(response.priceCapRule()).isEqualTo(PriceCapRule.FACE_VALUE);
    }

    // ---- update() authorization ----

    @Test
    void update_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(eventId, request(true, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resalePolicyRepository, accessGuard);
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

        assertThatThrownBy(() -> service.update(eventId, request(true, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resalePolicyRepository);
    }

    @Test
    void update_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.update(eventId, request(true, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_admin_bypassesOrgRoleCheck_withNoCurrentUserIdOrHasRoleCallAtAll() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(resalePolicyRepository.saveAndFlush(any(ResalePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        ResalePolicyResponse response = service.update(eventId, request(true, PriceCapRule.NONE, null));

        assertThat(response.enabled()).isTrue();
        verify(accessGuard, org.mockito.Mockito.never()).currentUserId();
        verify(accessGuard, org.mockito.Mockito.never()).hasRole(any(), any(), any());
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
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(resalePolicyRepository.saveAndFlush(any(ResalePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        ResalePolicyResponse response = service.update(eventId, request(true, PriceCapRule.FACE_VALUE, null));

        assertThat(response.enabled()).isTrue();
        assertThat(response.priceCapRule()).isEqualTo(PriceCapRule.FACE_VALUE);
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
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        ArgumentCaptor<ResalePolicy> captor = ArgumentCaptor.forClass(ResalePolicy.class);
        when(resalePolicyRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.update(eventId, request(true, PriceCapRule.FACE_VALUE_PLUS_FEE, new MoneyDto(500L, "USD")));

        ResalePolicy saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPriceCapRule()).isEqualTo(PriceCapRule.FACE_VALUE_PLUS_FEE);
        assertThat(saved.getFeeAmount().getAmount()).isEqualTo(500L);
        assertThat(saved.getFeeAmount().getCurrency()).isEqualTo("USD");
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
        ResalePolicy existing = ResalePolicy.builder()
                .id(existingPolicyId).eventId(eventId).enabled(false)
                .priceCapRule(PriceCapRule.NONE)
                .feeAmount(Money.builder().amount(999L).currency("USD").build())
                .build();
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
        when(resalePolicyRepository.save(any(ResalePolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        // Disabling and clearing the fee (feeAmount omitted from the request).
        ResalePolicyResponse response = service.update(eventId, request(false, PriceCapRule.FACE_VALUE, null));

        assertThat(response.enabled()).isFalse();
        assertThat(response.priceCapRule()).isEqualTo(PriceCapRule.FACE_VALUE);
        assertThat(response.feeAmount()).isNull();
        verify(resalePolicyRepository).save(existing);
        assertThat(existing.getId()).isEqualTo(existingPolicyId); // same row, not a duplicate
        assertThat(existing.getFeeAmount()).isNull();
    }
}
