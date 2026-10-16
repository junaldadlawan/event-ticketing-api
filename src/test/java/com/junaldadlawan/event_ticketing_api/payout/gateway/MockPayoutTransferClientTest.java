package com.junaldadlawan.event_ticketing_api.payout.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPayoutTransferClientTest {

    @Test
    void theSimulatedWallet_isActivated_andSaysItIsNotRealMoney() {
        PayoutTransferClient.WalletInfo wallet = new MockPayoutTransferClient().wallet();

        assertThat(wallet.provider()).isEqualTo("MOCK");
        assertThat(wallet.status()).isEqualTo("activated");
        assertThat(wallet.livemode()).isFalse();
        assertThat(wallet.availableBalance()).isPositive();
        assertThat(wallet.matchesConfiguredAccount()).isNull();
    }
}
