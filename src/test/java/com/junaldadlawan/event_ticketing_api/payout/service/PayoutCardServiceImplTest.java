package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutCardServiceImplTest {

    @Mock
    private PayoutCardRepository payoutCardRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private PayoutCardServiceImpl service;
    private UUID orgId;
    private UUID callerId;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new PayoutCardServiceImpl(payoutCardRepository, organizationRepository, accessGuard, org.mockito.Mockito.mock(com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService.class), "paymongo", true);
        orgId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        organization = Organization.builder().id(orgId).name("Org").status(OrganizationStatus.APPROVED).build();
        lenient().when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        lenient().when(payoutCardRepository.save(any(PayoutCard.class))).thenAnswer(inv -> {
            PayoutCard card = inv.getArgument(0);
            if (card.getId() == null) {
                card.setId(UUID.randomUUID());
            }
            return card;
        });
    }

    private void caller(boolean admin, boolean owner, boolean organizer) {
        lenient().when(accessGuard.isAdmin()).thenReturn(admin);
        lenient().when(accessGuard.currentUserId()).thenReturn(callerId);
        lenient().when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(owner);
        lenient().when(accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(organizer);
    }

    private PayoutCardRequest request(Boolean primary) {
        return new PayoutCardRequest("Demo Events Co.", "Visa", "4242", 12, YearMonth.now(ZoneOffset.UTC).getYear() + 2, "tok_test_12345678", primary);
    }

    private PayoutCard card(boolean primary) {
        return PayoutCard.builder().id(UUID.randomUUID()).organizationId(orgId).holderName("Demo Events Co.").brand("Visa")
                .last4("4242").expMonth(12).expYear(2099).paymentToken("tok_test_12345678").primary(primary).build();
    }

    @Test
    void add_theFirstCard_isAlwaysPrimary_evenWhenNotAsked() {
        caller(false, true, false);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);

        PayoutCardResponse response = service.add(orgId, request(false));

        assertThat(response.primary()).isTrue();
        assertThat(response.last4()).isEqualTo("4242");
        assertThat(response.toString()).doesNotContain("tok_test_12345678");
    }

    @Test
    void add_withTheMockPayoutGateway_alwaysSavesTheSameDemoCard_whateverWasSent() {
        service = new PayoutCardServiceImpl(payoutCardRepository, organizationRepository, accessGuard, org.mockito.Mockito.mock(com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService.class), "mock", true);
        caller(false, true, false);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);
        PayoutCardRequest other = new PayoutCardRequest("Demo Events Co.", "Mastercard", "1111", 1, 2001, "tok_other_12345678", null);

        PayoutCardResponse response = service.add(orgId, other);

        assertThat(response.brand()).isEqualTo("Visa");
        assertThat(response.last4()).isEqualTo("4242");
        assertThat(response.expYear()).isEqualTo(2099);
        assertThat(response.primary()).isTrue();
    }

    @Test
    void add_withPayMongoPayouts_usesTheCardThatWasSent_evenIfTheDemoFlagIsOn() {
        caller(false, true, false);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);
        PayoutCardRequest other = new PayoutCardRequest("Demo Events Co.", "Mastercard", "1111", 12, 2099, "tok_other_12345678", null);

        assertThat(service.add(orgId, other).last4()).isEqualTo("1111");
    }

    @Test
    void add_aLaterCard_isNotPrimaryUnlessAsked_andLeavesTheCurrentPrimaryAlone() {
        caller(false, true, false);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(1L);

        assertThat(service.add(orgId, request(null)).primary()).isFalse();
        verify(payoutCardRepository, never()).saveAndFlush(any());
    }

    @Test
    void add_asPrimary_unsetsTheOldPrimaryFirst_soThereAreNeverTwo() {
        caller(false, true, false);
        PayoutCard old = card(true);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(1L);
        when(payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(old));
        when(payoutCardRepository.saveAndFlush(old)).thenReturn(old);

        PayoutCardResponse response = service.add(orgId, request(true));

        assertThat(response.primary()).isTrue();
        assertThat(old.isPrimary()).isFalse();
        InOrder order = inOrder(payoutCardRepository);
        order.verify(payoutCardRepository).saveAndFlush(old);
        order.verify(payoutCardRepository).save(any(PayoutCard.class));
    }

    @Test
    void add_anAdmin_mayAddForAnyOrganization() {
        caller(true, false, false);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);

        assertThat(service.add(orgId, request(null)).primary()).isTrue();
    }

    @Test
    void add_anOrganizerOrStranger_isForbidden() {
        caller(false, false, true);

        assertThatThrownBy(() -> service.add(orgId, request(null))).isInstanceOf(ForbiddenException.class);
        verify(payoutCardRepository, never()).save(any());
    }

    @Test
    void add_onlyAnApprovedOrganization() {
        caller(false, true, false);
        organization.setStatus(OrganizationStatus.PENDING);

        assertThatThrownBy(() -> service.add(orgId, request(null)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("not approved");
        verify(payoutCardRepository, never()).save(any());
    }

    @Test
    void add_anExpiredCard_isRejected_butOneExpiringThisMonthIsFine() {
        caller(false, true, false);
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        YearMonth lastMonth = now.minusMonths(1);
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(0L);

        assertThatThrownBy(() -> service.add(orgId, new PayoutCardRequest("Holder", "Visa", "4242", lastMonth.getMonthValue(),
                lastMonth.getYear(), "tok_test_12345678", null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        assertThat(service.add(orgId, new PayoutCardRequest("Holder", "Visa", "4242", now.getMonthValue(), now.getYear(),
                "tok_test_12345678", null)).expMonth()).isEqualTo(now.getMonthValue());
    }

    @Test
    void add_unknownOrganization_throwsResourceNotFound() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(orgId, request(null))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_ownerOrganizerAndAdminMayRead_aStrangerMayNot() {
        PayoutCard primary = card(true);
        when(payoutCardRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(orgId)).thenReturn(List.of(primary));

        caller(false, true, false);
        assertThat(service.list(orgId)).hasSize(1);
        caller(false, false, true);
        assertThat(service.list(orgId)).hasSize(1);
        caller(true, false, false);
        assertThat(service.list(orgId)).hasSize(1);
        caller(false, false, false);
        assertThatThrownBy(() -> service.list(orgId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void setPrimary_swapsThePrimary_oldOneFirst() {
        caller(false, true, false);
        PayoutCard old = card(true);
        PayoutCard next = card(false);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(next.getId())).thenReturn(Optional.of(next));
        when(payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(old));
        when(payoutCardRepository.saveAndFlush(old)).thenReturn(old);

        PayoutCardResponse response = service.setPrimary(next.getId());

        assertThat(response.primary()).isTrue();
        assertThat(old.isPrimary()).isFalse();
        InOrder order = inOrder(payoutCardRepository);
        order.verify(payoutCardRepository).saveAndFlush(old);
        order.verify(payoutCardRepository).save(next);
    }

    @Test
    void setPrimary_anOrganizer_isForbidden() {
        caller(false, false, true);
        PayoutCard next = card(false);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(next.getId())).thenReturn(Optional.of(next));

        assertThatThrownBy(() -> service.setPrimary(next.getId())).isInstanceOf(ForbiddenException.class);
        assertThat(next.isPrimary()).isFalse();
    }

    @Test
    void remove_aNonPrimaryCard_softDeletes() {
        caller(false, true, false);
        PayoutCard other = card(false);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(other.getId())).thenReturn(Optional.of(other));

        service.remove(other.getId());

        assertThat(other.getDeletedAt()).isNotNull();
        verify(payoutCardRepository).save(other);
    }

    @Test
    void remove_thePrimaryWhileOthersExist_isRefused() {
        caller(false, true, false);
        PayoutCard primary = card(true);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(primary.getId())).thenReturn(Optional.of(primary));
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(2L);

        assertThatThrownBy(() -> service.remove(primary.getId())).isInstanceOf(ConflictException.class);
        assertThat(primary.getDeletedAt()).isNull();
        assertThat(primary.isPrimary()).isTrue();
    }

    @Test
    void remove_theOnlyCard_evenThePrimary_isAllowed() {
        caller(false, true, false);
        PayoutCard only = card(true);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(only.getId())).thenReturn(Optional.of(only));
        when(payoutCardRepository.countByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(1L);

        service.remove(only.getId());

        assertThat(only.getDeletedAt()).isNotNull();
    }

    @Test
    void remove_anOrganizer_isForbidden() {
        caller(false, false, true);
        PayoutCard other = card(false);
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.remove(other.getId())).isInstanceOf(ForbiddenException.class);
        assertThat(other.getDeletedAt()).isNull();
    }

    @Test
    void remove_unknownCard_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(payoutCardRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
