package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.Status;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.TransferRequest;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.TransferResult;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutAccountRepository;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import com.junaldadlawan.event_ticketing_api.payout.security.AccountNumberCipher;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
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

/** Sending a payout through the payment provider, settling it from the provider's answer or webhook, and the guards around both. */
@ExtendWith(MockitoExtension.class)
class PayoutServiceImplSendTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PayoutCardRepository payoutCardRepository;
    @Mock
    private PayoutAccountRepository payoutAccountRepository;
    @Mock
    private PayoutTransferClient transferClient;
    @Mock
    private AccountNumberCipher cipher;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private com.junaldadlawan.event_ticketing_api.event.repository.EventRepository eventRepository;

    @Mock
    private com.junaldadlawan.event_ticketing_api.payout.repository.PayoutEligibilityRepository eligibilityRepository;
    @Mock
    private com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService auditLogService;

    private PayoutServiceImpl service;
    private UUID orgId;
    private Payout payout;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, organizationRepository, accessGuard, orderRepository, refundRepository,
                payoutCardRepository, payoutAccountRepository, transferClient, cipher, transactionManager,
                eventRepository, eligibilityRepository, auditLogService);
        orgId = UUID.randomUUID();
        payout = Payout.builder().id(UUID.randomUUID()).organizationId(orgId)
                .gross(php(10_500)).fees(php(500)).net(php(10_000))
                .periodStart(LocalDate.of(2026, 8, 1)).periodEnd(LocalDate.of(2026, 8, 31)).status(PayoutStatus.SCHEDULED).build();
        lenient().when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        lenient().when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
    }

    private static Money php(long amount) {
        return Money.builder().amount(amount).currency("PHP").build();
    }

    private PayoutAccount account() {
        PayoutAccount account = PayoutAccount.builder().id(UUID.randomUUID()).organizationId(orgId).bankName("BDO").bankCode("BNORPHMM")
                .accountName("Demo Events Co.").accountNumberEncrypted("encrypted").accountNumberLast4("7890").build();
        when(transferClient.requiresAccount()).thenReturn(true);
        when(payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(account));
        when(cipher.decrypt("encrypted")).thenReturn("1234567890");
        return account;
    }

    // ---- send() ----

    @Test
    void send_toABankAccount_claimsThePayout_sendsTheNetAmountToTheDecryptedAccount_andKeepsItProcessingWhilePending() {
        PayoutAccount account = account();
        when(transferClient.send(any())).thenReturn(new TransferResult("tr_123", Status.PENDING, null));

        PayoutResponse response = service.send(payout.getId());

        ArgumentCaptor<TransferRequest> sent = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferClient).send(sent.capture());
        assertThat(sent.getValue().amount()).isEqualTo(10_000L);
        assertThat(sent.getValue().currency()).isEqualTo("PHP");
        assertThat(sent.getValue().reference()).isEqualTo(payout.getId().toString());
        assertThat(sent.getValue().bankCode()).isEqualTo("BNORPHMM");
        assertThat(sent.getValue().accountName()).isEqualTo("Demo Events Co.");
        assertThat(sent.getValue().accountNumber()).isEqualTo("1234567890");
        assertThat(response.status()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(response.transferId()).isEqualTo("tr_123");
        assertThat(response.payoutAccountId()).isEqualTo(account.getId());
        assertThat(payout.getTransferAttempts()).isEqualTo(1);
        verify(accessGuard).requireAdmin();
    }

    @Test
    void send_aTransferThatSucceedsAtOnce_marksThePayoutPaid() {
        when(transferClient.send(any())).thenReturn(new TransferResult("mock_tr_1", Status.SUCCEEDED, null));

        PayoutResponse response = service.send(payout.getId());

        assertThat(response.status()).isEqualTo(PayoutStatus.PAID);
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void send_aTransferPayMongoRejects_marksThePayoutFailedWithTheReason_andItCanBeSentAgainWithANewReference() {
        account();
        when(transferClient.send(any()))
                .thenReturn(new TransferResult(null, Status.FAILED, "insufficient_wallet_balance"))
                .thenReturn(new TransferResult("tr_2", Status.PENDING, null));

        PayoutResponse failed = service.send(payout.getId());
        assertThat(failed.status()).isEqualTo(PayoutStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("insufficient_wallet_balance");

        PayoutResponse retried = service.send(payout.getId());

        ArgumentCaptor<TransferRequest> sent = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferClient, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(1).reference()).isEqualTo(payout.getId() + "-2");
        assertThat(retried.status()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(retried.failureReason()).isNull();
        assertThat(payout.getTransferAttempts()).isEqualTo(2);
    }

    @Test
    void send_whenTheProviderCannotBeReached_marksItFailedWithAWarningToCheckTheDashboard_andRethrows() {
        account();
        when(transferClient.send(any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "unavailable"));

        assertThatThrownBy(() -> service.send(payout.getId())).isInstanceOf(ApiException.class);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getFailureReason()).contains("Check the provider dashboard");
    }

    @Test
    void send_aPayoutThatIsProcessingOrPaid_isRefused_andNothingIsSent() {
        for (PayoutStatus status : new PayoutStatus[] {PayoutStatus.PROCESSING, PayoutStatus.PAID}) {
            payout.setStatus(status);
            assertThatThrownBy(() -> service.send(payout.getId())).isInstanceOf(ConflictException.class);
        }
        verify(transferClient, never()).send(any());
    }

    @Test
    void send_aPayoutWithNothingToSend_isRefused() {
        payout.setNet(php(0));

        assertThatThrownBy(() -> service.send(payout.getId())).isInstanceOf(ConflictException.class).hasMessageContaining("nothing to send");
        verify(transferClient, never()).send(any());
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    void send_whenTheOrganizationHasNoPayoutAccount_isRefused_andThePayoutStaysScheduled() {
        when(transferClient.requiresAccount()).thenReturn(true);
        when(payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(payout.getId())).isInstanceOf(ConflictException.class).hasMessageContaining("payout account");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        verify(transferClient, never()).send(any());
    }

    @Test
    void send_anUnknownPayout_is404_andANonAdminIsForbidden() {
        UUID unknown = UUID.randomUUID();
        when(payoutRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(unknown)).isInstanceOf(ResourceNotFoundException.class);

        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        assertThatThrownBy(() -> service.send(payout.getId())).isInstanceOf(ForbiddenException.class);
        verify(transferClient, never()).send(any());
    }

    // ---- wallet() ----

    @Test
    void wallet_isAdminOnly_andReturnsWhatTheClientReports() {
        when(transferClient.wallet()).thenReturn(new PayoutTransferClient.WalletInfo("PAYMONGO", "wallet_1", "activated", false, true, "basic",
                "2026-05-24T06:10:06.117Z", 1_500_000L, 500_000L, new PayoutTransferClient.WalletLimits(1, 2, 3, 4, 5),
                new PayoutTransferClient.WalletUsage(6, 7, 8, 9), new PayoutTransferClient.WalletAccount("paymongo", "Test", "0000000001", "PHP"),
                "2026-05-24T06:10:03.105Z", "not a date", true));

        var wallet = service.wallet();

        verify(accessGuard).requireAdmin();
        assertThat(wallet.activated()).isTrue();
        assertThat(wallet.balance().available().amount()).isEqualTo(1_500_000L);
        assertThat(wallet.balance().pending().currency()).isEqualTo("PHP");
        assertThat(wallet.limits().outwardMonthly().amount()).isEqualTo(5L);
        assertThat(wallet.usage().inwardDaily().amount()).isEqualTo(6L);
        assertThat(wallet.statementOfAcceptanceSignedAt()).isNotNull();
        assertThat(wallet.updatedAt()).isNull();   // an unreadable date never breaks the response
        assertThat(wallet.matchesConfiguredAccount()).isTrue();

        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        assertThatThrownBy(() -> service.wallet()).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void wallet_aDeactivatedWallet_isNotActivated() {
        when(transferClient.wallet()).thenReturn(new PayoutTransferClient.WalletInfo("PAYMONGO", "wallet_1", "deactivated", true, true, null,
                null, 0, 0, new PayoutTransferClient.WalletLimits(0, 0, 0, 0, 0), new PayoutTransferClient.WalletUsage(0, 0, 0, 0), null, null, null, null));

        assertThat(service.wallet().activated()).isFalse();
        assertThat(service.wallet().account()).isNull();
    }

    // ---- refresh() and the webhook ----

    @Test
    void refresh_asksTheProviderAndSettlesAProcessingPayout() {
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setTransferId("tr_123");
        when(transferClient.retrieve("tr_123")).thenReturn(new TransferResult("tr_123", Status.SUCCEEDED, null));

        PayoutResponse response = service.refresh(payout.getId());

        assertThat(response.status()).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    void refresh_aPayoutThatIsNotProcessing_isRefused() {
        assertThatThrownBy(() -> service.refresh(payout.getId())).isInstanceOf(ConflictException.class);
        verify(transferClient, never()).retrieve(any());
    }

    @Test
    void onTransferResult_settlesTheProcessingPayoutOnce_andIgnoresRepeatsAndUnknownTransfers() {
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setTransferId("tr_123");
        when(payoutRepository.findByTransferIdForUpdate("tr_123")).thenReturn(Optional.of(payout));
        when(payoutRepository.findByTransferIdForUpdate("tr_unknown")).thenReturn(Optional.empty());

        service.onTransferResult("tr_123", false, "AC03: invalid account");
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getFailureReason()).isEqualTo("AC03: invalid account");

        service.onTransferResult("tr_123", true, null);   // a late or repeated event never flips a settled payout
        service.onTransferResult("tr_unknown", true, null);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
    }

    @Test
    void onTransferResult_aSuccessfulTransfer_marksThePayoutPaid() {
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setTransferId("tr_123");
        when(payoutRepository.findByTransferIdForUpdate("tr_123")).thenReturn(Optional.of(payout));

        service.onTransferResult("tr_123", true, null);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
    }
}
