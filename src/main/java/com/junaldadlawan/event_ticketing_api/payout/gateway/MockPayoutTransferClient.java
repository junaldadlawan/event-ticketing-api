package com.junaldadlawan.event_ticketing_api.payout.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Used whenever {@code payments.payout-gateway} is not paymongo: the transfer "succeeds" at once and no money moves anywhere. */
@Component
@ConditionalOnExpression("'${payments.payout-gateway:mock}' != 'paymongo'")
public class MockPayoutTransferClient implements PayoutTransferClient {

    @Override
    public boolean requiresAccount() {
        return false;
    }

    @Override
    public List<Bank> banks() {
        return List.of(new Bank("Test Bank", "TESTPHMMXXX"));
    }

    @Override
    public TransferResult send(TransferRequest request) {
        return new TransferResult("mock_tr_" + UUID.randomUUID(), Status.SUCCEEDED, null);
    }

    @Override
    public WalletInfo wallet() {
        long unlimited = 999_999_999_999_999_999L;
        return new WalletInfo("MOCK", "mock_wallet", "activated", false, true, "basic", null, 100_000_000L, 0L,
                new WalletLimits(unlimited, unlimited, unlimited, unlimited, unlimited), new WalletUsage(0, 0, 0, 0),
                new WalletAccount("mock", "Mock Platform Wallet", "0000000000", "PHP"), null, null, null);
    }

    @Override
    public TransferResult retrieve(String transferId) {
        return new TransferResult(transferId, Status.SUCCEEDED, null);
    }
}
