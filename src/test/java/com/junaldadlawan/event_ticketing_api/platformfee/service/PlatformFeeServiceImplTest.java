package com.junaldadlawan.event_ticketing_api.platformfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.EffectivePlatformFeeResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleRequest;
import com.junaldadlawan.event_ticketing_api.platformfee.dto.PlatformFeeRuleResponse;
import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeType;
import com.junaldadlawan.event_ticketing_api.platformfee.repository.PlatformFeeRuleRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformFeeServiceImplTest {

    @Mock
    private PlatformFeeRuleRepository ruleRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private PlatformFeeServiceImpl service;

    private UUID orgId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        service = new PlatformFeeServiceImpl(ruleRepository, eventRepository, organizationRepository, accessGuard);
        orgId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        // no rules unless a test says otherwise
        lenient().when(ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
        lenient().when(ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
    }

    private PlatformFeeRule percentage(FeeScope scope, UUID scopeId, String percent) {
        return PlatformFeeRule.builder().id(UUID.randomUUID()).scope(scope).scopeId(scopeId).type(FeeType.PERCENTAGE)
                .percentage(new BigDecimal(percent)).build();
    }

    private PlatformFeeRule flat(FeeScope scope, UUID scopeId, long amount, String currency) {
        return PlatformFeeRule.builder().id(UUID.randomUUID()).scope(scope).scopeId(scopeId).type(FeeType.FLAT)
                .flatAmount(amount).flatCurrency(currency).build();
    }

    private void platformRule(PlatformFeeRule rule) {
        lenient().when(ruleRepository.findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope.PLATFORM)).thenReturn(Optional.of(rule));
    }

    private void orgRule(PlatformFeeRule rule) {
        lenient().when(ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(FeeScope.ORGANIZATION, orgId)).thenReturn(Optional.of(rule));
    }

    private void eventRule(PlatformFeeRule rule) {
        lenient().when(ruleRepository.findByScopeAndScopeIdAndDeletedAtIsNull(FeeScope.EVENT, eventId)).thenReturn(Optional.of(rule));
    }

    // ---- quote(): the amount ----

    @Test
    void quote_noRuleAnywhere_isNoFee() {
        assertThat(service.quote(orgId, eventId, 10_000, "USD")).isEqualTo(PlatformFeeQuote.NONE);
    }

    @Test
    void quote_percentage_isAPercentOfTheTicketTotal() {
        platformRule(percentage(FeeScope.PLATFORM, null, "5"));

        PlatformFeeQuote quote = service.quote(orgId, eventId, 10_000, "USD");

        assertThat(quote.amount()).isEqualTo(500L);
        assertThat(quote.scope()).isEqualTo(FeeScope.PLATFORM);
        assertThat(quote.type()).isEqualTo(FeeType.PERCENTAGE);
        assertThat(quote.percentage()).isEqualByComparingTo("5");
        assertThat(quote.flatAmount()).isNull();
    }

    @Test
    void quote_percentage_roundsHalfUpToTheMinorUnit() {
        platformRule(percentage(FeeScope.PLATFORM, null, "2.5"));

        assertThat(service.quote(orgId, eventId, 999, "USD").amount()).isEqualTo(25L);   // 24.975
        assertThat(service.quote(orgId, eventId, 1000, "USD").amount()).isEqualTo(25L);  // 25.0
        assertThat(service.quote(orgId, eventId, 10, "USD").amount()).isEqualTo(0L);     // 0.25
        assertThat(service.quote(orgId, eventId, 30, "USD").amount()).isEqualTo(1L);     // 0.75
    }

    @Test
    void quote_flat_isAFixedAmountPerOrder_inTheSameCurrency() {
        platformRule(flat(FeeScope.PLATFORM, null, 250, "USD"));

        PlatformFeeQuote quote = service.quote(orgId, eventId, 10_000, "usd");

        assertThat(quote.amount()).isEqualTo(250L);
        assertThat(quote.type()).isEqualTo(FeeType.FLAT);
        assertThat(quote.flatAmount()).isEqualTo(250L);
        assertThat(quote.percentage()).isNull();
    }

    @Test
    void quote_flatInAnotherCurrencyThanTheOrder_isSkipped() {
        platformRule(flat(FeeScope.PLATFORM, null, 250, "EUR"));

        assertThat(service.quote(orgId, eventId, 10_000, "USD")).isEqualTo(PlatformFeeQuote.NONE);
    }

    @Test
    void quote_aFreeOrder_neverPaysAFee_evenWithAFlatRule() {
        platformRule(flat(FeeScope.PLATFORM, null, 250, "USD"));

        assertThat(service.quote(orgId, eventId, 0, "USD")).isEqualTo(PlatformFeeQuote.NONE);
        assertThat(service.quote(orgId, eventId, -5, "USD")).isEqualTo(PlatformFeeQuote.NONE);
    }

    @Test
    void quote_aZeroRateIsAWaiver_thatStillRecordsWhichRuleAppliedIt() {
        platformRule(percentage(FeeScope.PLATFORM, null, "10"));
        eventRule(percentage(FeeScope.EVENT, eventId, "0"));

        PlatformFeeQuote quote = service.quote(orgId, eventId, 10_000, "USD");

        assertThat(quote.amount()).isZero();
        assertThat(quote.scope()).isEqualTo(FeeScope.EVENT);
    }

    // ---- quote(): which rule wins ----

    @Test
    void quote_theEventRuleBeatsTheOrganizationRuleAndThePlatformDefault() {
        platformRule(percentage(FeeScope.PLATFORM, null, "10"));
        orgRule(percentage(FeeScope.ORGANIZATION, orgId, "7"));
        eventRule(flat(FeeScope.EVENT, eventId, 123, "USD"));

        PlatformFeeQuote quote = service.quote(orgId, eventId, 10_000, "USD");

        assertThat(quote.scope()).isEqualTo(FeeScope.EVENT);
        assertThat(quote.amount()).isEqualTo(123L);
    }

    @Test
    void quote_withoutAnEventRule_theOrganizationRuleBeatsThePlatformDefault() {
        platformRule(percentage(FeeScope.PLATFORM, null, "10"));
        orgRule(percentage(FeeScope.ORGANIZATION, orgId, "7"));

        PlatformFeeQuote quote = service.quote(orgId, eventId, 10_000, "USD");

        assertThat(quote.scope()).isEqualTo(FeeScope.ORGANIZATION);
        assertThat(quote.amount()).isEqualTo(700L);
    }

    @Test
    void quote_withNeitherOverride_thePlatformDefaultApplies() {
        platformRule(percentage(FeeScope.PLATFORM, null, "10"));

        assertThat(service.quote(orgId, eventId, 10_000, "USD").amount()).isEqualTo(1000L);
    }

    // ---- upsert() ----

    @Test
    void upsert_createsTheDefaultRule_forAPercentage() {
        when(ruleRepository.save(any(PlatformFeeRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PlatformFeeRuleResponse response = service.upsert(FeeScope.PLATFORM, null,
                new PlatformFeeRuleRequest(FeeType.PERCENTAGE, new BigDecimal("7.5"), null));

        assertThat(response.scope()).isEqualTo(FeeScope.PLATFORM);
        assertThat(response.scopeId()).isNull();
        assertThat(response.type()).isEqualTo(FeeType.PERCENTAGE);
        assertThat(response.percentage()).isEqualByComparingTo("7.5");
        assertThat(response.flatAmount()).isNull();
        verify(accessGuard).requireAdmin();
    }

    @Test
    void upsert_forAnOrganization_createsItsRule_whenItExists() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(ruleRepository.save(any(PlatformFeeRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PlatformFeeRuleResponse response = service.upsert(FeeScope.ORGANIZATION, orgId,
                new PlatformFeeRuleRequest(FeeType.FLAT, null, new MoneyDto(300, "USD")));

        assertThat(response.scope()).isEqualTo(FeeScope.ORGANIZATION);
        assertThat(response.scopeId()).isEqualTo(orgId);
        assertThat(response.flatAmount()).isEqualTo(new MoneyDto(300, "USD"));
        assertThat(response.percentage()).isNull();
    }

    @Test
    void upsert_aSecondTime_replacesTheExistingRule_insteadOfAddingAnother() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        PlatformFeeRule existing = percentage(FeeScope.ORGANIZATION, orgId, "5");
        orgRule(existing);
        when(ruleRepository.save(any(PlatformFeeRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PlatformFeeRuleResponse response = service.upsert(FeeScope.ORGANIZATION, orgId,
                new PlatformFeeRuleRequest(FeeType.FLAT, null, new MoneyDto(300, "USD")));

        ArgumentCaptor<PlatformFeeRule> saved = ArgumentCaptor.forClass(PlatformFeeRule.class);
        verify(ruleRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(existing.getType()).isEqualTo(FeeType.FLAT);
        assertThat(existing.getPercentage()).isNull();
        assertThat(existing.getFlatAmount()).isEqualTo(300L);
    }

    @Test
    void upsert_aZeroRate_isAccepted() {
        when(ruleRepository.save(any(PlatformFeeRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(FeeScope.PLATFORM, null, new PlatformFeeRuleRequest(FeeType.PERCENTAGE, BigDecimal.ZERO, null));
        service.upsert(FeeScope.PLATFORM, null, new PlatformFeeRuleRequest(FeeType.FLAT, null, new MoneyDto(0, "USD")));
    }

    @Test
    void upsert_forAnUnknownOrganizationOrEvent_throwsResourceNotFound_andSavesNothing() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());
        PlatformFeeRuleRequest request = new PlatformFeeRuleRequest(FeeType.PERCENTAGE, BigDecimal.ONE, null);

        assertThatThrownBy(() -> service.upsert(FeeScope.ORGANIZATION, orgId, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.upsert(FeeScope.EVENT, eventId, request)).isInstanceOf(ResourceNotFoundException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void upsert_aMismatchedRate_throwsBadRequest() {
        assertThatThrownBy(() -> service.upsert(FeeScope.PLATFORM, null, new PlatformFeeRuleRequest(FeeType.PERCENTAGE, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upsert(FeeScope.PLATFORM, null,
                new PlatformFeeRuleRequest(FeeType.PERCENTAGE, BigDecimal.ONE, new MoneyDto(1, "USD"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upsert(FeeScope.PLATFORM, null, new PlatformFeeRuleRequest(FeeType.FLAT, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upsert(FeeScope.PLATFORM, null,
                new PlatformFeeRuleRequest(FeeType.FLAT, BigDecimal.ONE, new MoneyDto(1, "USD"))))
                .isInstanceOf(BadRequestException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void upsert_aNonAdmin_isForbidden_andNothingElseIsTouched() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.upsert(FeeScope.PLATFORM, null, new PlatformFeeRuleRequest(FeeType.PERCENTAGE, BigDecimal.ONE, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(ruleRepository, never()).save(any());
    }

    // ---- delete() / list() / effective() ----

    @Test
    void delete_softDeletesTheRule_soTheNextLessSpecificOneApplies() {
        PlatformFeeRule existing = percentage(FeeScope.EVENT, eventId, "5");
        eventRule(existing);

        service.delete(FeeScope.EVENT, eventId);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(ruleRepository).save(existing);
        verify(ruleRepository, never()).delete(any());
    }

    @Test
    void delete_theDefault_andAMissingRule() {
        PlatformFeeRule existing = percentage(FeeScope.PLATFORM, null, "5");
        platformRule(existing);

        service.delete(FeeScope.PLATFORM, null);
        assertThat(existing.getDeletedAt()).isNotNull();

        assertThatThrownBy(() -> service.delete(FeeScope.ORGANIZATION, orgId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_aNonAdmin_isForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.delete(FeeScope.PLATFORM, null)).isInstanceOf(ForbiddenException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void list_returnsEveryLiveRule_orOnlyOneScope() {
        PlatformFeeRule a = percentage(FeeScope.PLATFORM, null, "5");
        PlatformFeeRule b = flat(FeeScope.EVENT, eventId, 100, "USD");
        when(ruleRepository.findByDeletedAtIsNullOrderByScopeAscCreatedAtAsc()).thenReturn(List.of(a, b));
        when(ruleRepository.findByScopeAndDeletedAtIsNullOrderByCreatedAtAsc(FeeScope.EVENT)).thenReturn(List.of(b));

        assertThat(service.list(null)).extracting(PlatformFeeRuleResponse::id).containsExactly(a.getId(), b.getId());
        assertThat(service.list(FeeScope.EVENT)).extracting(PlatformFeeRuleResponse::id).containsExactly(b.getId());
        verify(accessGuard, org.mockito.Mockito.times(2)).requireAdmin();
    }

    @Test
    void effective_reportsTheRuleThatAppliesToTheEvent_orNullWhenNone() {
        Event event = Event.builder().id(eventId).organizationId(orgId).build();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        PlatformFeeRule org = percentage(FeeScope.ORGANIZATION, orgId, "7");

        EffectivePlatformFeeResponse none = service.effective(eventId);
        orgRule(org);
        EffectivePlatformFeeResponse withRule = service.effective(eventId);

        assertThat(none.rule()).isNull();
        assertThat(none.organizationId()).isEqualTo(orgId);
        assertThat(withRule.rule().id()).isEqualTo(org.getId());
        assertThat(withRule.rule().scope()).isEqualTo(FeeScope.ORGANIZATION);
    }

    @Test
    void effective_unknownEvent_throwsResourceNotFound() {
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.effective(eventId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
