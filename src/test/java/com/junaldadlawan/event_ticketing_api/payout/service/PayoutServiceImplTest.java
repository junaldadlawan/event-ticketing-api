package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link PayoutServiceImpl} (no Spring context) —
 * mirrors {@code ResalePolicyServiceImplTest}'s owner/organizer/admin
 * authorization style. There is no payout-generation job anywhere in this
 * codebase (per the dispatch), so every {@code Payout} row here is seeded
 * directly rather than produced by any real flow.
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private PayoutServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, organizationRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Payout payout() {
        return Payout.builder()
                .id(UUID.randomUUID())
                .organizationId(orgId)
                .gross(Money.builder().amount(10_000L).currency("USD").build())
                .fees(Money.builder().amount(500L).currency("USD").build())
                .net(Money.builder().amount(9_500L).currency("USD").build())
                .periodStart(LocalDate.of(2026, 8, 1))
                .periodEnd(LocalDate.of(2026, 8, 31))
                .status(PayoutStatus.PAID)
                .build();
    }

    @Test
    void list_unknownOrganization_throwsResourceNotFound() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(orgId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard, payoutRepository);
    }

    @Test
    void list_owner_returnsPagedPayouts() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID ownerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payout()), pageable, 1));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).organizationId()).isEqualTo(orgId);
        assertThat(result.getContent().get(0).net().amount()).isEqualTo(9_500L);
        assertThat(result.getContent().get(0).status()).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    void list_organizer_returnsPagedPayouts() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID organizerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void list_admin_bypassesOrgRoleCheck() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.isAdmin()).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payout()), pageable, 1));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void list_nonMemberStranger_throwsForbidden() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID strangerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(orgId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(payoutRepository);
    }

    @Test
    void list_crossOrgOwner_throwsForbidden() {
        UUID otherOrgId = UUID.randomUUID();
        when(organizationRepository.existsById(otherOrgId)).thenReturn(true);
        UUID ownerOfDifferentOrgId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerOfDifferentOrgId);
        when(accessGuard.hasRole(ownerOfDifferentOrgId, otherOrgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(ownerOfDifferentOrgId, otherOrgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(otherOrgId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }
}
