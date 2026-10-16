package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeCheckResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample;
import com.junaldadlawan.event_ticketing_api.gatewayfee.repository.GatewayFeeSettingRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayFeeCheckServiceImplTest {

    @Mock
    private GatewayFeeSettingRepository settingRepository;
    @Mock
    private PaymentFeeSampler sampler;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private GatewayFeeCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GatewayFeeCheckServiceImpl(settingRepository, sampler, accessGuard);
    }

    private void setting(String percentage, long flat) {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(GatewayFeeSetting.builder()
                .percentage(new BigDecimal(percentage)).flatAmount(flat).flatCurrency("PHP").payer(GatewayFeePayer.CUSTOMER).build()));
    }

    /** The real PayMongo test payment: PHP 105.00 by a US Visa, fee 18.68 + foreign fee 1.05, net 85.27. */
    private static PaymentSample usVisa() {
        return new PaymentSample("pay_1", 10_500, "PHP", 1_868, 105, 0, 8_527, "card", "visa", "US", 1_791_999_766L, false);
    }

    private static PaymentSample domestic() {
        return new PaymentSample("pay_2", 10_000, "PHP", 1_850, 0, 0, 8_150, "card", "visa", "PH", 1_791_999_000L, false);   // 3.5% + 15.00 of 100.00
    }

    @Test
    void anInternationalCardPayment_isRecognised_andTheSettingIsComparedWithIt() {
        setting("6", 1_600);
        when(sampler.provider()).thenReturn("PAYMONGO");
        when(sampler.recentPaidPayments(5)).thenReturn(List.of(usVisa()));

        GatewayFeeCheckResponse check = service.check(5);

        assertThat(check.provider()).isEqualTo("PAYMONGO");
        assertThat(check.configured().percentage()).isEqualByComparingTo("6");
        GatewayFeeCheckResponse.Sample s = check.samples().get(0);
        assertThat(s.totalFee().amount()).isEqualTo(1_973L);   // 18.68 + 1.05
        assertThat(s.netAmount().amount()).isEqualTo(8_527L);
        assertThat(s.effectivePercent()).isEqualByComparingTo("18.79");
        assertThat(s.matchedRate()).isEqualTo("INTERNATIONAL_CARD");
        assertThat(s.expectedWithConfigured().amount()).isEqualTo(2_230L);   // 6% of 105.00 + 16.00
        assertThat(s.differenceFromConfigured().amount()).isEqualTo(-257L);
        assertThat(s.matchesConfigured()).isFalse();
        assertThat(check.allSamplesMatchConfigured()).isFalse();
        assertThat(check.suggestedSetting().basedOn()).isEqualTo("INTERNATIONAL_CARD");
        assertThat(check.suggestedSetting().percentage()).isEqualByComparingTo("4.5");
        assertThat(check.suggestedSetting().flatAmount().amount()).isEqualTo(1_500L);
        assertThat(check.note()).isNull();
        verify(accessGuard).requireAdmin();
    }

    @Test
    void aForeignCard_isComparedWithTheForeignRate_andADomesticCardWithTheDomesticOne() {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(GatewayFeeSetting.builder()
                .percentage(new BigDecimal("3.5")).flatAmount(1_500).flatCurrency("PHP").payer(GatewayFeePayer.CUSTOMER)
                .foreignPercentage(new BigDecimal("4.5")).foreignFlatAmount(1_500L).build()));
        when(sampler.provider()).thenReturn("PAYMONGO");
        when(sampler.recentPaidPayments(5)).thenReturn(List.of(usVisa(), domestic()));

        GatewayFeeCheckResponse check = service.check(5);

        assertThat(check.configured().foreignPercentage()).isEqualByComparingTo("4.5");
        assertThat(check.configured().foreignFlatAmount().amount()).isEqualTo(1_500L);
        GatewayFeeCheckResponse.Sample foreign = check.samples().get(0);
        assertThat(foreign.cardOrigin()).isEqualTo("FOREIGN");
        assertThat(foreign.expectedWithConfigured().amount()).isEqualTo(1_973L);   // 4.5% of 105.00 + 15.00, not the domestic 3.5%
        assertThat(foreign.matchesConfigured()).isTrue();
        GatewayFeeCheckResponse.Sample local = check.samples().get(1);
        assertThat(local.cardOrigin()).isEqualTo("DOMESTIC");
        assertThat(local.expectedWithConfigured().amount()).isEqualTo(1_850L);
        assertThat(local.matchesConfigured()).isTrue();
        assertThat(check.allSamplesMatchConfigured()).isTrue();
    }

    @Test
    void aSettingThatMatchesWhatTheGatewayCharged_isReportedAsMatching() {
        setting("4.5", 1_500);
        when(sampler.provider()).thenReturn("PAYMONGO");
        when(sampler.recentPaidPayments(5)).thenReturn(List.of(usVisa()));

        GatewayFeeCheckResponse check = service.check(5);

        assertThat(check.samples().get(0).matchesConfigured()).isTrue();
        assertThat(check.samples().get(0).differenceFromConfigured().amount()).isZero();
        assertThat(check.allSamplesMatchConfigured()).isTrue();
    }

    @Test
    void aDomesticPayment_isRecognised_andTheNewestRecognisedPaymentSetsTheSuggestion() {
        setting("4.5", 1_500);
        when(sampler.provider()).thenReturn("PAYMONGO");
        when(sampler.recentPaidPayments(5)).thenReturn(List.of(domestic(), usVisa()));

        GatewayFeeCheckResponse check = service.check(5);

        assertThat(check.samples()).extracting(GatewayFeeCheckResponse.Sample::matchedRate).containsExactly("DOMESTIC_CARD", "INTERNATIONAL_CARD");
        assertThat(check.suggestedSetting().basedOn()).isEqualTo("DOMESTIC_CARD");
        assertThat(check.allSamplesMatchConfigured()).isFalse();   // the domestic payment was charged less than 4.5% + 15.00
    }

    @Test
    void aFeeThatFitsNoKnownRate_isNotRecognised_andNothingIsSuggested() {
        setting("4.5", 1_500);
        when(sampler.provider()).thenReturn("PAYMONGO");
        when(sampler.recentPaidPayments(5)).thenReturn(List.of(new PaymentSample("pay_3", 10_000, "PHP", 999, 0, 0, 9_001, "gcash", null, null, null, false)));

        GatewayFeeCheckResponse check = service.check(5);

        assertThat(check.samples().get(0).matchedRate()).isNull();
        assertThat(check.samples().get(0).paidAt()).isNull();
        assertThat(check.suggestedSetting()).isNull();
    }

    @Test
    void noPayments_saysWhy_forPayMongoAndForMock() {
        setting("0", 0);
        when(sampler.recentPaidPayments(5)).thenReturn(List.of());
        when(sampler.provider()).thenReturn("PAYMONGO");
        assertThat(service.check(5).note()).contains("no paid payments yet");

        when(sampler.provider()).thenReturn("MOCK");
        GatewayFeeCheckResponse mock = service.check(5);
        assertThat(mock.note()).contains("mock mode");
        assertThat(mock.samples()).isEmpty();
        assertThat(mock.allSamplesMatchConfigured()).isFalse();
    }

    @Test
    void theLimitIsChecked_andOnlyAnAdminMayAsk() {
        assertThatThrownBy(() -> service.check(0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.check(21)).isInstanceOf(BadRequestException.class);

        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        assertThatThrownBy(() -> service.check(5)).isInstanceOf(ForbiddenException.class);
        verify(sampler, never()).recentPaidPayments(org.mockito.ArgumentMatchers.anyInt());
    }
}
