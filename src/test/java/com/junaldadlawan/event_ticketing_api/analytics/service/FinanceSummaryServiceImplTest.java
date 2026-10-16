package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository.Figures;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository.PayoutTotal;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSummaryServiceImplTest {

    @Mock
    private FinanceSummaryRepository repository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeActualSyncService gatewayFeeActualSync;

    private FinanceSummaryServiceImpl service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new FinanceSummaryServiceImpl(repository, organizationRepository, accessGuard, gatewayFeeActualSync);
        orgId = UUID.randomUUID();
    }

    private static Figures figures() {
        return new Figures(3, 1, 1, 2, 10_000, 1_000, 900, 800, 100, 400, 300, 50, 50, 8_100, 2_000, 3_000, 3_100);
    }

    // ---- the period ----

    @Test
    void thePeriodDefaultsToTheLast30Days_bothEndsIncluded_andIsCheckedForSense() {
        FinancePeriod period = FinancePeriod.resolve(null, LocalDate.of(2026, 10, 31));

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 10, 2));   // 30 days ending on the 31st
        assertThat(period.fromInstant()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(period.toInstant()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));   // the whole last day
        assertThat(FinancePeriod.resolve(null, null).to()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(FinancePeriod.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)).toInstant()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));

        assertThatThrownBy(() -> FinancePeriod.resolve(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FinancePeriod.resolve(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2))).isInstanceOf(BadRequestException.class);
        FinancePeriod.resolve(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));   // 366 days later: allowed
    }

    // ---- the admin's summary ----

    @Test
    void platformSummary_mapsEveryFigure_andNetIncomeIsFeesMinusTheGatewayCostTheyBear() {
        when(repository.figures(any(), any(), eq("PHP"), isNull())).thenReturn(figures());
        when(repository.payoutsByStatus(any(), any(), eq("PHP"), isNull())).thenReturn(Map.of("PAID", new PayoutTotal(2, 5_000)));
        when(repository.otherCurrencies(any(), any(), eq("PHP"), isNull())).thenReturn(List.of("USD"));

        var summary = service.platformSummary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null);

        verify(accessGuard).requireAdmin();
        assertThat(summary.currency()).isEqualTo("PHP");
        assertThat(summary.otherCurrencies()).containsExactly("USD");
        assertThat(summary.orders().count()).isEqualTo(3);
        assertThat(summary.sales().net().amount()).isEqualTo(9_000L);   // 10,000 - 1,000
        assertThat(summary.platform().feeIncome().amount()).isEqualTo(900L);
        assertThat(summary.platform().gatewayCostBorne().amount()).isEqualTo(50L);
        assertThat(summary.platform().netIncome().amount()).isEqualTo(850L);
        assertThat(summary.gatewayFees().foreignCardOrders()).isEqualTo(1);
        assertThat(summary.gatewayFees().unknownFeeOrders()).isEqualTo(2);
        verify(gatewayFeeActualSync).fillMissing();
        assertThat(summary.organizers().owedNotYetInPayout().amount()).isEqualTo(3_100L);
        assertThat(summary.payouts().paid().count()).isEqualTo(2);
        assertThat(summary.payouts().paid().net().amount()).isEqualTo(5_000L);
        assertThat(summary.payouts().failed().count()).isZero();   // a status with no payouts is zero, never missing
        assertThat(summary.payouts().failed().net().currency()).isEqualTo("PHP");
    }

    @Test
    void platformSummary_takesTheCurrencyInAnyCase_butOnlyAThreeLetterCode() {
        when(repository.figures(any(), any(), eq("USD"), isNull())).thenReturn(figures());
        when(repository.payoutsByStatus(any(), any(), eq("USD"), isNull())).thenReturn(Map.of());
        when(repository.otherCurrencies(any(), any(), eq("USD"), isNull())).thenReturn(List.of());

        assertThat(service.platformSummary(null, null, " usd ").currency()).isEqualTo("USD");
        assertThatThrownBy(() -> service.platformSummary(null, null, "PESO")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.platformSummary(null, null, "12")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void platformSummary_isAdminOnly_andReadsNothingOtherwise() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.platformSummary(null, null, null)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    // ---- an organization's summary ----

    @Test
    void organizationSummary_forItsOwner_showsOnlyItsOwnFigures() {
        UUID ownerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(repository.figures(any(), any(), eq("PHP"), eq(orgId))).thenReturn(figures());
        when(repository.payoutsByStatus(any(), any(), eq("PHP"), eq(orgId))).thenReturn(Map.of());
        when(repository.otherCurrencies(any(), any(), eq("PHP"), eq(orgId))).thenReturn(List.of());

        var summary = service.organizationSummary(orgId, null, null, "PHP");

        assertThat(summary.organizationId()).isEqualTo(orgId);
        assertThat(summary.sales().gross().amount()).isEqualTo(10_000L);
        assertThat(summary.deductions().platformFee().amount()).isEqualTo(800L);
        assertThat(summary.deductions().gatewayFee().amount()).isEqualTo(100L);
        assertThat(summary.deductions().total().amount()).isEqualTo(900L);
        assertThat(summary.income().amount()).isEqualTo(8_100L);
        assertThat(summary.paidOut().amount()).isEqualTo(2_000L);
        assertThat(summary.inPayouts().amount()).isEqualTo(3_000L);
        assertThat(summary.owedNotYetInPayout().amount()).isEqualTo(3_100L);
    }

    @Test
    void organizationSummary_anAdminMayLookAtAnyOrganization() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.isAdmin()).thenReturn(true);
        when(repository.figures(any(), any(), any(), eq(orgId))).thenReturn(figures());
        when(repository.payoutsByStatus(any(), any(), any(), eq(orgId))).thenReturn(Map.of());
        when(repository.otherCurrencies(any(), any(), any(), eq(orgId))).thenReturn(List.of());

        assertThat(service.organizationSummary(orgId, null, null, null).income().amount()).isEqualTo(8_100L);
    }

    @Test
    void organizationSummary_anyoneElse_isForbidden_andAnUnknownOrganizationIs404() {
        UUID callerId = UUID.randomUUID();
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)).thenReturn(false);   // an organizer, a member, or another organization's owner

        assertThatThrownBy(() -> service.organizationSummary(orgId, null, null, null)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).figures(any(), any(), any(), any());

        UUID unknown = UUID.randomUUID();
        when(organizationRepository.existsById(unknown)).thenReturn(false);
        assertThatThrownBy(() -> service.organizationSummary(unknown, null, null, null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
