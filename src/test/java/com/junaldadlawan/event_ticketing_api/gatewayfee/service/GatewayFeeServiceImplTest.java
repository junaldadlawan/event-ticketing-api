package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingRequest;
import com.junaldadlawan.event_ticketing_api.gatewayfee.dto.GatewayFeeSettingResponse;
import com.junaldadlawan.event_ticketing_api.gatewayfee.entity.GatewayFeeSetting;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.gatewayfee.repository.GatewayFeeSettingRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayFeeServiceImplTest {

    @Mock
    private GatewayFeeSettingRepository settingRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private GatewayFeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GatewayFeeServiceImpl(settingRepository, accessGuard);
    }

    private void setting(String percentage, long flat, String currency, GatewayFeePayer payer) {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(GatewayFeeSetting.builder()
                .percentage(new BigDecimal(percentage)).flatAmount(flat).flatCurrency(currency).payer(payer).build()));
    }

    // ---- the fee ----

    @Test
    void quote_isThePercentageOfTheBase_roundedHalfUp() {
        setting("4.02", 0, "PHP", GatewayFeePayer.CUSTOMER);

        GatewayFeeQuote quote = service.quote(10500, "PHP");   // 10500 centavos x 4.02% = 422.1 -> 422

        assertThat(quote.amount()).isEqualTo(422L);
        assertThat(quote.payer()).isEqualTo(GatewayFeePayer.CUSTOMER);
        assertThat(quote.percentage()).isEqualByComparingTo("4.02");
        assertThat(quote.chargedToBuyer()).isEqualTo(422L);
    }

    @Test
    void quote_addsTheFlatPart_whenTheOrderIsInTheFlatCurrency() {
        setting("4.02", 1339, "PHP", GatewayFeePayer.ORGANIZATION);

        GatewayFeeQuote quote = service.quote(10500, "PHP");

        assertThat(quote.amount()).isEqualTo(422L + 1339L);   // 4.02% of PHP 105.00 plus PHP 13.39
        assertThat(quote.flatAmount()).isEqualTo(1339L);
        assertThat(quote.chargedToBuyer()).isZero();          // only the CUSTOMER payer charges the buyer
    }

    @Test
    void quote_supportsAThreeDecimalPercentagePlusAFixedAmount_likePayMongosCardRate() {
        setting("3.125", 1339, "PHP", GatewayFeePayer.CUSTOMER);

        GatewayFeeQuote quote = service.quote(10000, "PHP");   // PHP 100.00: 3.125% = 3.125 -> 313 centavos (half up), plus 13.39

        assertThat(quote.amount()).isEqualTo(313L + 1339L);
        assertThat(quote.percentage()).isEqualByComparingTo("3.125");
        assertThat(quote.flatAmount()).isEqualTo(1339L);
    }

    @Test
    void quote_aFlatAmountInAnotherCurrency_isSkipped() {
        setting("0", 1339, "PHP", GatewayFeePayer.CUSTOMER);

        assertThat(service.quote(10500, "USD")).isEqualTo(GatewayFeeQuote.NONE);
    }

    @Test
    void quote_roundsHalfUpToTheMinorUnit() {
        setting("1.00", 0, "PHP", GatewayFeePayer.PLATFORM);

        assertThat(service.quote(150, "PHP").amount()).isEqualTo(2L);   // 1.5 -> 2
        assertThat(service.quote(149, "PHP").amount()).isEqualTo(1L);   // 1.49 -> 1
    }

    @Test
    void quote_isNothing_forAFreeOrder_ZeroSettings_orNoSetting() {
        setting("5.00", 100, "PHP", GatewayFeePayer.CUSTOMER);
        assertThat(service.quote(0, "PHP")).isEqualTo(GatewayFeeQuote.NONE);
        assertThat(service.quote(-5, "PHP")).isEqualTo(GatewayFeeQuote.NONE);

        setting("0", 0, "PHP", GatewayFeePayer.CUSTOMER);
        assertThat(service.quote(10000, "PHP")).isEqualTo(GatewayFeeQuote.NONE);

        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.empty());
        assertThat(service.quote(10000, "PHP")).isEqualTo(GatewayFeeQuote.NONE);
    }

    @Test
    void quote_needsNoLogin_becauseTheBuyersCartAndCheckoutCallIt() {
        setting("1.00", 0, "PHP", GatewayFeePayer.CUSTOMER);

        service.quote(10000, "PHP");

        verifyNoInteractions(accessGuard);
    }

    // ---- a second rate for cards issued abroad ----

    private void settingWithForeign(String percentage, long flat, String foreignPercentage, Long foreignFlat) {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(GatewayFeeSetting.builder()
                .percentage(new BigDecimal(percentage)).flatAmount(flat).flatCurrency("PHP").payer(GatewayFeePayer.CUSTOMER)
                .foreignPercentage(foreignPercentage == null ? null : new BigDecimal(foreignPercentage)).foreignFlatAmount(foreignFlat).build()));
    }

    @Test
    void quote_withAForeignRate_chargesTheDomesticFeeInTheCart_andKeepsWhatAForeignCardWouldCost() {
        settingWithForeign("3.5", 1500, "4.5", 1500L);

        GatewayFeeQuote quote = service.quote(10_000, "PHP");

        assertThat(quote.amount()).isEqualTo(350L + 1500L);   // what the customer pays: the card is not known yet
        assertThat(quote.chargedToBuyer()).isEqualTo(1850L);
        assertThat(quote.foreignAmount()).isEqualTo(450L + 1500L);
        assertThat(quote.foreignPercentage()).isEqualByComparingTo("4.5");
        assertThat(quote.foreignFlatAmount()).isEqualTo(1500L);
    }

    @Test
    void quote_withoutAForeignRate_aForeignCardCostsTheDomesticFee() {
        settingWithForeign("3.5", 1500, null, null);

        GatewayFeeQuote quote = service.quote(10_000, "PHP");

        assertThat(quote.foreignAmount()).isEqualTo(quote.amount());
        assertThat(quote.foreignPercentage()).isNull();
    }

    @Test
    void quote_aForeignFlatAmountInAnotherCurrency_isSkippedLikeTheDomesticOne() {
        settingWithForeign("3.5", 1500, "4.5", 1500L);

        GatewayFeeQuote quote = service.quote(10_000, "USD");

        assertThat(quote.amount()).isEqualTo(350L);
        assertThat(quote.foreignAmount()).isEqualTo(450L);
    }

    @Test
    void update_storesTheForeignRate_andShowsIt() {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(new GatewayFeeSetting()));
        when(settingRepository.save(any(GatewayFeeSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayFeeSettingResponse response = service.update(new GatewayFeeSettingRequest(new BigDecimal("3.5"), new MoneyDto(1500, "PHP"),
                GatewayFeePayer.CUSTOMER, new BigDecimal("4.5"), new MoneyDto(1500, "PHP")));

        assertThat(response.percentage()).isEqualByComparingTo("3.5");
        assertThat(response.foreignPercentage()).isEqualByComparingTo("4.5");
        assertThat(response.foreignFlatAmount()).isEqualTo(new MoneyDto(1500, "PHP"));
    }

    @Test
    void update_withoutAForeignRate_clearsTheOneThatWasThere() {
        GatewayFeeSetting existing = GatewayFeeSetting.builder().foreignPercentage(new BigDecimal("4.5")).foreignFlatAmount(1500L).flatCurrency("PHP").build();
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(GatewayFeeSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayFeeSettingResponse response = service.update(new GatewayFeeSettingRequest(new BigDecimal("3.5"), null, GatewayFeePayer.CUSTOMER));

        assertThat(existing.getForeignPercentage()).isNull();
        assertThat(existing.getForeignFlatAmount()).isNull();
        assertThat(response.foreignPercentage()).isNull();
        assertThat(response.foreignFlatAmount()).isNull();
    }

    @Test
    void update_aForeignFlatAmountInAnotherCurrencyThanTheDomesticOne_isBadRequest_andNothingIsSaved() {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(new GatewayFeeSetting()));

        assertThatThrownBy(() -> service.update(new GatewayFeeSettingRequest(new BigDecimal("3.5"), new MoneyDto(1500, "PHP"),
                GatewayFeePayer.CUSTOMER, new BigDecimal("4.5"), new MoneyDto(1500, "USD")))).isInstanceOf(BadRequestException.class);
        verify(settingRepository, never()).save(any());
    }

    // ---- the admin setting ----

    @Test
    void update_savesPercentageFlatAndPayer() {
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(new GatewayFeeSetting()));
        when(settingRepository.save(any(GatewayFeeSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayFeeSettingResponse response = service.update(new GatewayFeeSettingRequest(new BigDecimal("3.125"),
                new MoneyDto(1339, "PHP"), GatewayFeePayer.CUSTOMER));

        assertThat(response.percentage()).isEqualByComparingTo("3.125");
        assertThat(response.flatAmount()).isEqualTo(new MoneyDto(1339, "PHP"));
        assertThat(response.payer()).isEqualTo(GatewayFeePayer.CUSTOMER);
        verify(accessGuard).requireAdmin();
    }

    @Test
    void update_withoutAFlatAmount_clearsTheFlatPart() {
        GatewayFeeSetting existing = GatewayFeeSetting.builder().flatAmount(1339).flatCurrency("PHP").build();
        when(settingRepository.findById(GatewayFeeSetting.ROW_ID)).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(GatewayFeeSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(new GatewayFeeSettingRequest(new BigDecimal("3.00"), null, GatewayFeePayer.ORGANIZATION));

        assertThat(existing.getFlatAmount()).isZero();
        assertThat(existing.getPayer()).isEqualTo(GatewayFeePayer.ORGANIZATION);
    }

    @Test
    void getAndUpdate_areAdminOnly() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.get()).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(new GatewayFeeSettingRequest(BigDecimal.ONE, null, GatewayFeePayer.CUSTOMER)))
                .isInstanceOf(ForbiddenException.class);
        verify(settingRepository, never()).save(any());
    }

    @Test
    void get_returnsTheCurrentSetting() {
        setting("2.50", 0, "PHP", GatewayFeePayer.ORGANIZATION);

        GatewayFeeSettingResponse response = service.get();

        assertThat(response.percentage()).isEqualByComparingTo("2.50");
        assertThat(response.payer()).isEqualTo(GatewayFeePayer.ORGANIZATION);
    }
}
