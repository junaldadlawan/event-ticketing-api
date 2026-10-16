package com.junaldadlawan.event_ticketing_api.payout.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.Status;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.TransferRequest;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient.TransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** PayMongo transfers against a stub server: what is sent (v2 batch transfer, centavos, the right rail, Basic auth) and how every answer maps. */
class PayMongoPayoutTransferClientTest {

    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("sk_test_not_a_real_key:".getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private PayMongoPayoutTransferClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://paymongo.test/v1").defaultHeader(HttpHeaders.AUTHORIZATION, BASIC);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PayMongoPayoutTransferClient(builder.build(), "https://paymongo.test/v1", "0000000001", "Test Platform Inc.");
    }

    private TransferRequest request(long amount, String currency) {
        return new TransferRequest("ref-1", amount, currency, "BNORPHMM", "Demo Events Co.", "1234567890", "Event ticket payout", Map.of("payout_id", "p1"));
    }

    private void respond(HttpStatus status, String json) {
        server.expect(requestTo("https://paymongo.test/v2/batch_transfers")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    @Test
    void requiresAnAccount() {
        assertThat(client.requiresAccount()).isTrue();
    }

    @Test
    void send_postsABatchOfOneToTheV2Api_inCentavosByInstaPay_fromTheWallet_andReturnsThePendingTransfer() {
        server.expect(requestTo("https://paymongo.test/v2/batch_transfers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andExpect(jsonPath("$.transfers.length()").value(1))
                .andExpect(jsonPath("$.transfers[0].provider").value("instapay"))
                .andExpect(jsonPath("$.transfers[0].amount").value(10_000))
                .andExpect(jsonPath("$.transfers[0].currency").value("PHP"))
                .andExpect(jsonPath("$.transfers[0].reference_number").value("ref-1"))
                .andExpect(jsonPath("$.transfers[0].source_account.number").value("0000000001"))
                .andExpect(jsonPath("$.transfers[0].source_account.bic").value("PAEYPHM2XXX"))
                .andExpect(jsonPath("$.transfers[0].destination_account.number").value("1234567890"))
                .andExpect(jsonPath("$.transfers[0].destination_account.name").value("Demo Events Co."))
                .andExpect(jsonPath("$.transfers[0].destination_account.bic").value("BNORPHMM"))
                .andExpect(jsonPath("$.transfers[0].metadata.payout_id").value("p1"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"id\":\"btr_1\",\"transfers\":[{\"id\":\"tr_1\",\"status\":\"pending\",\"fee\":1000}]}}"));

        TransferResult result = client.send(request(10_000, "PHP"));

        assertThat(result).isEqualTo(new TransferResult("tr_1", Status.PENDING, null));
        server.verify();
    }

    @Test
    void send_anAmountAboveTheInstaPayLimit_goesByPesoNet() {
        server.expect(requestTo("https://paymongo.test/v2/batch_transfers")).andExpect(jsonPath("$.transfers[0].provider").value("pesonet"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"transfers\":[{\"id\":\"tr_2\",\"status\":\"pending\"}]}}"));

        client.send(request(5_000_001, "PHP"));

        server.verify();
    }

    @Test
    void send_aTransferThatAlreadySucceeded_orFailed_isReportedAsSuch() {
        respond(HttpStatus.OK, "{\"data\":{\"transfers\":[{\"id\":\"tr_3\",\"status\":\"succeeded\"}]}}");
        assertThat(client.send(request(100, "PHP")).status()).isEqualTo(Status.SUCCEEDED);

        server.reset();
        respond(HttpStatus.OK, "{\"data\":{\"transfers\":[{\"id\":\"tr_4\",\"status\":\"failed\",\"provider_error_code\":\"AC03\",\"provider_error\":\"Invalid account\"}]}}");
        TransferResult failed = client.send(request(100, "PHP"));
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.failureReason()).isEqualTo("AC03: Invalid account");
    }

    @Test
    void send_aRejectionPayMongoStates_isAFailedResult_notAnException() {
        respond(HttpStatus.BAD_REQUEST, "{\"errors\":[{\"code\":\"parameter_invalid\",\"detail\":\"Wallet balance is not enough\"}]}");

        TransferResult result = client.send(request(100, "PHP"));

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.transferId()).isNull();
        assertThat(result.failureReason()).isEqualTo("Wallet balance is not enough");
    }

    @Test
    void send_whenTheOutcomeCannotBeKnown_throwsBadGateway() {
        respond(HttpStatus.INTERNAL_SERVER_ERROR, "{}");
        assertThatThrownBy(() -> client.send(request(100, "PHP"))).isInstanceOf(ApiException.class).hasMessageContaining("could not be confirmed");

        server.reset();
        server.expect(requestTo("https://paymongo.test/v2/batch_transfers")).andRespond(request -> { throw new IOException("timeout"); });
        assertThatThrownBy(() -> client.send(request(100, "PHP"))).isInstanceOf(ApiException.class);

        server.reset();
        respond(HttpStatus.OK, "{\"data\":{\"transfers\":[]}}");   // accepted but no transfer back: cannot tell
        assertThatThrownBy(() -> client.send(request(100, "PHP"))).isInstanceOf(ApiException.class);
    }

    @Test
    void send_onlyInPhp_andOnlyWithAConfiguredWallet() {
        assertThatThrownBy(() -> client.send(request(100, "USD"))).isInstanceOf(BadRequestException.class);

        PayMongoPayoutTransferClient noWallet = new PayMongoPayoutTransferClient(RestClient.builder().build(), "https://paymongo.test/v1", "", "");
        assertThatThrownBy(() -> noWallet.send(request(100, "PHP"))).isInstanceOf(ApiException.class).hasMessageContaining("PAYMONGO_WALLET_ACCOUNT_NUMBER");
    }

    @Test
    void retrieve_readsTheTransfersStatus() {
        server.expect(requestTo("https://paymongo.test/v2/transfers/tr_9")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"data\":{\"id\":\"tr_9\",\"status\":\"succeeded\"}}"));
        assertThat(client.retrieve("tr_9")).isEqualTo(new TransferResult("tr_9", Status.SUCCEEDED, null));

        server.reset();
        server.expect(requestTo("https://paymongo.test/v2/transfers/tr_8"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"id\":\"tr_8\",\"attributes\":{\"status\":\"failed\",\"provider_error_code\":\"AC04\"}}}"));
        TransferResult failed = client.retrieve("tr_8");
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.failureReason()).isEqualTo("AC04");
    }

    private static final String WALLET = """
            {"data":{"id":"wallet_b","merchant_id":"org_1","livemode":false,"is_default":true,"status":"activated","type":"basic","statement_of_acceptance_signed_at":"2026-05-24T06:10:06.117Z","balance":{"available":1500000,"pending":500000},"limits":{"balance":2500000000,"transactions":{"inward":{"daily":999999999999999999,"monthly":1500000000},"outward":{"daily":100000000,"monthly":999999999999999999}}},"running_transaction":{"inward":{"daily":1,"monthly":2},"outward":{"daily":3,"monthly":4}},"account":{"provider":"paymongo","account_name":"Test Platform Inc.","account_number":"0000000001","currency":"PHP"},"created_at":"2026-05-24T06:10:03.105Z","updated_at":"2026-08-15T03:11:35.684Z"}}""";

    private void expectWalletList(String json) {
        server.expect(requestTo("https://paymongo.test/v2/wallets/")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(json));
    }

    @Test
    void wallet_picksTheDefaultWallet_readsItWithTheThreeFields_andReturnsEveryDetail() {
        expectWalletList("{\"data\":[{\"id\":\"wallet_a\",\"is_default\":false},{\"id\":\"wallet_b\",\"is_default\":true}]}");
        server.expect(requestTo("https://paymongo.test/v2/wallets/wallet_b?fields=limits&fields=balance&fields=account"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, BASIC))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(WALLET));

        PayoutTransferClient.WalletInfo wallet = client.wallet();

        assertThat(wallet.provider()).isEqualTo("PAYMONGO");
        assertThat(wallet.id()).isEqualTo("wallet_b");
        assertThat(wallet.status()).isEqualTo("activated");
        assertThat(wallet.livemode()).isFalse();
        assertThat(wallet.isDefault()).isTrue();
        assertThat(wallet.type()).isEqualTo("basic");
        assertThat(wallet.statementOfAcceptanceSignedAt()).isEqualTo("2026-05-24T06:10:06.117Z");
        assertThat(wallet.availableBalance()).isEqualTo(1_500_000L);
        assertThat(wallet.pendingBalance()).isEqualTo(500_000L);
        assertThat(wallet.limits()).isEqualTo(new PayoutTransferClient.WalletLimits(2_500_000_000L, 999_999_999_999_999_999L, 1_500_000_000L,
                100_000_000L, 999_999_999_999_999_999L));
        assertThat(wallet.usage()).isEqualTo(new PayoutTransferClient.WalletUsage(1, 2, 3, 4));
        assertThat(wallet.account()).isEqualTo(new PayoutTransferClient.WalletAccount("paymongo", "Test Platform Inc.", "0000000001", "PHP"));
        assertThat(wallet.createdAt()).isEqualTo("2026-05-24T06:10:03.105Z");
        assertThat(wallet.matchesConfiguredAccount()).isTrue();   // the configured wallet number is 0000000001
        server.verify();
    }

    @Test
    void wallet_saysWhenItIsNotTheConfiguredWallet_andTakesTheFirstWhenNoneIsMarkedDefault() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://paymongo.test/v1").defaultHeader(HttpHeaders.AUTHORIZATION, BASIC);
        MockRestServiceServer other = MockRestServiceServer.bindTo(builder).build();
        PayMongoPayoutTransferClient differentWallet = new PayMongoPayoutTransferClient(builder.build(), "https://paymongo.test/v1", "9999999999", "Other");
        other.expect(requestTo("https://paymongo.test/v2/wallets/"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"data\":[{\"id\":\"wallet_b\"}]}"));
        other.expect(requestTo("https://paymongo.test/v2/wallets/wallet_b?fields=limits&fields=balance&fields=account"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(WALLET));

        PayoutTransferClient.WalletInfo wallet = differentWallet.wallet();

        assertThat(wallet.id()).isEqualTo("wallet_b");
        assertThat(wallet.matchesConfiguredAccount()).isFalse();
    }

    @Test
    void wallet_aDeactivatedWalletIsReportedAsIs() {
        expectWalletList("{\"data\":[{\"id\":\"wallet_b\",\"is_default\":true}]}");
        server.expect(requestTo("https://paymongo.test/v2/wallets/wallet_b?fields=limits&fields=balance&fields=account"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"id\":\"wallet_b\",\"status\":\"deactivated\",\"balance\":{\"available\":0,\"pending\":0}}}"));

        PayoutTransferClient.WalletInfo wallet = client.wallet();

        assertThat(wallet.status()).isEqualTo("deactivated");
        assertThat(wallet.availableBalance()).isZero();
        assertThat(wallet.account().accountNumber()).isNull();
        assertThat(wallet.matchesConfiguredAccount()).isNull();   // no number to compare
    }

    @Test
    void wallet_withNoWalletOnTheAccount_is404WithAHint_andAnOutageIs502() {
        expectWalletList("{\"data\":[]}");
        assertThatThrownBy(() -> client.wallet()).isInstanceOf(com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException.class)
                .hasMessageContaining("Activate one in the PayMongo Dashboard");

        server.reset();
        server.expect(requestTo("https://paymongo.test/v2/wallets/")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.wallet()).isInstanceOf(ApiException.class);
    }

    @Test
    void anHtmlAnswer_suchAsARedirectPage_is502_neverAnUnhandled500() {
        server.expect(requestTo("https://paymongo.test/v2/wallets/")).andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
                .contentType(MediaType.TEXT_HTML).body("<a href=\"/v2/wallets/\">Moved Permanently</a>."));
        assertThatThrownBy(() -> client.wallet()).isInstanceOf(ApiException.class).hasMessageContaining("could not be confirmed");

        server.reset();
        server.expect(requestTo("https://paymongo.test/v2/batch_transfers")).andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.TEXT_HTML).body("<html></html>"));
        assertThatThrownBy(() -> client.send(request(100, "PHP"))).isInstanceOf(ApiException.class);
    }

    @Test
    void banks_listsTheInstitutionsThatCanReceive() {
        server.expect(requestTo("https://paymongo.test/v1/wallets/receiving_institutions?provider=instapay")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"data":[
                          {"id":"1","attributes":{"name":"BDO Unibank","provider_code":"BNORPHMM","type":["sender","receiver"]}},
                          {"id":"2","attributes":{"name":"Send Only Bank","provider_code":"SENDPHMM","type":["sender"]}},
                          {"id":"3","attributes":{"name":"GCash","provider_code":"GCSHPHM2","type":["receiver"]}}]}"""));

        assertThat(client.banks()).containsExactly(new PayoutTransferClient.Bank("BDO Unibank", "BNORPHMM"),
                new PayoutTransferClient.Bank("GCash", "GCSHPHM2"));
    }
}
