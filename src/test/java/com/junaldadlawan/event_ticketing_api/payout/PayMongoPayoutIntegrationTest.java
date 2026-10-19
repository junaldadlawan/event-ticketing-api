package com.junaldadlawan.event_ticketing_api.payout;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.gateway.PayMongoWebhookVerifier;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayMongoPayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paying an organizer through PayMongo end to end on the real database: the bank account is stored encrypted, a payout is generated to
 * it, an admin sends it (PayMongo itself is stubbed), and the signed {@code transfer.outward.*} webhook settles it. The encryption key
 * and wallet below are test values made up for this class.
 */
@SpringBootTest(properties = {"payments.gateway=paymongo", "payments.payout-gateway=paymongo", "payments.paymongo.secret-key=sk_test_not_a_real_key",
        "payments.paymongo.webhook-secret=whsk_test_secret", "payments.paymongo.wallet-account-number=0000000001",
        "payments.paymongo.wallet-account-name=Test Platform Inc.",
        "payouts.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@AutoConfigureMockMvc
class PayMongoPayoutIntegrationTest {

    @MockitoSpyBean
    private PayMongoPayoutTransferClient transferClient;
    @MockitoSpyBean
    private com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PayMongoPaymentFeeSampler feeSampler;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PayMongoWebhookVerifier verifier;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrderRepository orderRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    private UUID orgId;
    private String admin;
    private String customer;

    @BeforeEach
    void setUp() {
        Organization org = organizationRepository.save(Organization.builder().name("PayMongo Payout Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgId = org.getId();
        orgIds.add(orgId);
        admin = token(Role.ADMIN);
        customer = token(Role.CUSTOMER);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : orgIds) {
            jdbc.update("update orders set payout_id = null where payee_id = ?", id);
            jdbc.update("delete from payouts where organization_id = ?", id);
            jdbc.update("delete from payout_accounts where organization_id = ?", id);
        }
        orderIds.forEach(orderRepository::deleteById);
        actorIds.forEach(id -> jdbc.update("delete from audit_log_entries where actor_id = ?", id));
        orgIds.forEach(organizationRepository::deleteById);
    }

    // ---- fixtures ----

    private String token(Role role) {
        UUID id = UUID.randomUUID();
        actorIds.add(id);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id).email("payout-it-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private static final String ACCOUNT = "{\"bankCode\":\"BNORPHMM\",\"bankName\":\"BDO\",\"accountName\":\"Demo Events Co.\",\"accountNumber\":\"1234567890\"}";

    private ResultActions putAccount(String caller, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/organizations/{id}/payout-account", orgId).header("Authorization", caller)
                .contentType("application/json").content(body));
    }

    /** A paid order of the organization worth PHP 100.00 with no fees, so the payout's net is 10000 centavos. */
    private void paidOrder() {
        Order order = orderRepository.save(Order.builder().buyerId(UUID.randomUUID()).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                .status(OrderStatus.PAID).total(Money.builder().amount(10_000L).currency("PHP").build()).createdBy("payout-it").build());
        orderIds.add(order.getId());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode generate() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return json(mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin)
                .contentType("application/json").content("{\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today + "\"}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions transferWebhook(String type, String transferId, String error) throws Exception {
        String body = "{\"data\":{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"event\",\"attributes\":{\"type\":\"" + type
                + "\",\"livemode\":false,\"data\":{\"id\":\"wallet_tr_x\",\"type\":\"wallet_transaction\",\"attributes\":{\"transfer_id\":\""
                + transferId + "\",\"status\":\"x\"" + (error == null ? "" : ",\"provider_error_code\":\"AC03\",\"provider_error\":\"" + error + "\"")
                + "}}}}}";
        String timestamp = "1700000000";
        return mockMvc.perform(post("/api/v1/webhooks/paymongo").contentType("application/json")
                .header("Paymongo-Signature", "t=" + timestamp + ",te=" + verifier.sign(timestamp + "." + body) + ",li=")
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String payoutStatus(String id) {
        return jdbc.queryForObject("select status from payouts where id = ?::uuid", String.class, id);
    }

    // ---- the payout account ----

    @Test
    void theAccountNumberIsStoredEncrypted_andOnlyTheLast4AreEverShown() throws Exception {
        putAccount(admin, ACCOUNT).andExpect(status().isOk()).andExpect(jsonPath("$.accountNumberLast4").value("7890"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist()).andExpect(jsonPath("$.accountNumberEncrypted").doesNotExist());

        String stored = jdbc.queryForObject("select account_number_encrypted from payout_accounts where organization_id = ?", String.class, orgId);
        assertThat(stored).isNotBlank().doesNotContain("1234567890");
        mockMvc.perform(get("/api/v1/organizations/{id}/payout-account", orgId).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("BDO")).andExpect(jsonPath("$.accountNumberLast4").value("7890"));

        // setting it again replaces it: still one account
        putAccount(admin, ACCOUNT.replace("1234567890", "9876543210").replace("BDO", "BPI")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumberLast4").value("3210"));
        assertThat(jdbc.queryForObject("select count(*) from payout_accounts where organization_id = ? and deleted_at is null", Integer.class, orgId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/organizations/{id}/payout-account", orgId).header("Authorization", admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/organizations/{id}/payout-account", orgId).header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void theAccountIsRefused_whenInvalid_orNotAllowed() throws Exception {
        putAccount(admin, ACCOUNT.replace("BNORPHMM", "bdo")).andExpect(status().isBadRequest());
        putAccount(admin, ACCOUNT.replace("1234567890", "12ab")).andExpect(status().isBadRequest());
        putAccount(customer, ACCOUNT).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizations/{id}/payout-account", orgId).header("Authorization", customer)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/organizations/{id}/payout-account", orgId).contentType("application/json").content(ACCOUNT))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from payout_accounts where organization_id = ?", Integer.class, orgId)).isZero();
    }

    // ---- generate, send, settle ----

    @Test
    void aPayoutCannotBeGeneratedWithoutAPayoutAccount() throws Exception {
        paidOrder();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin).contentType("application/json")
                        .content("{\"periodStart\":\"" + today.minusDays(1) + "\",\"periodEnd\":\"" + today + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anAdminSendsAPayout_andTheSignedWebhookSettlesItPaid() throws Exception {
        putAccount(admin, ACCOUNT).andExpect(status().isOk());
        paidOrder();
        JsonNode payout = generate();
        String id = payout.get("id").asText();
        assertThat(payout.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(payout.get("payoutAccountId").isNull()).isFalse();
        assertThat(payout.get("net").get("amount").asLong()).isEqualTo(10_000L);
        doReturn(new PayoutTransferClient.TransferResult("tr_it_" + id.substring(0, 8), PayoutTransferClient.Status.PENDING, null))
                .when(transferClient).send(any());

        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", customer)).andExpect(status().isForbidden());
        verify(transferClient, never()).send(any());
        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING")).andExpect(jsonPath("$.transferId").value("tr_it_" + id.substring(0, 8)));

        ArgumentCaptor<PayoutTransferClient.TransferRequest> sent = ArgumentCaptor.forClass(PayoutTransferClient.TransferRequest.class);
        verify(transferClient).send(sent.capture());
        assertThat(sent.getValue().amount()).isEqualTo(10_000L);
        assertThat(sent.getValue().accountNumber()).isEqualTo("1234567890");   // decrypted only to send
        assertThat(sent.getValue().bankCode()).isEqualTo("BNORPHMM");
        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", admin)).andExpect(status().isConflict());   // never twice

        transferWebhook("transfer.outward.successful", "tr_it_" + id.substring(0, 8), null).andExpect(status().isOk());
        transferWebhook("transfer.outward.successful", "tr_it_" + id.substring(0, 8), null).andExpect(status().isOk());   // delivered twice

        assertThat(payoutStatus(id)).isEqualTo("PAID");
        mockMvc.perform(get("/api/v1/organizations/{id}/payouts", orgId).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    void aFailedTransfer_marksThePayoutFailedWithTheReason_andItCanBeSentAgain() throws Exception {
        putAccount(admin, ACCOUNT).andExpect(status().isOk());
        paidOrder();
        String id = generate().get("id").asText();
        doReturn(new PayoutTransferClient.TransferResult("tr_it_fail_" + id.substring(0, 8), PayoutTransferClient.Status.PENDING, null),
                new PayoutTransferClient.TransferResult("tr_it_retry_" + id.substring(0, 8), PayoutTransferClient.Status.PENDING, null))
                .when(transferClient).send(any());
        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", admin)).andExpect(status().isOk());

        transferWebhook("transfer.outward.failed", "tr_it_fail_" + id.substring(0, 8), "Invalid account").andExpect(status().isOk());

        assertThat(payoutStatus(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select failure_reason from payouts where id = ?::uuid", String.class, id)).isEqualTo("AC03: Invalid account");
        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING")).andExpect(jsonPath("$.failureReason").doesNotExist());
        assertThat(jdbc.queryForObject("select transfer_attempts from payouts where id = ?::uuid", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void aTransferWebhookWithABadSignature_orForAnUnknownTransfer_changesNothing() throws Exception {
        putAccount(admin, ACCOUNT).andExpect(status().isOk());
        paidOrder();
        String id = generate().get("id").asText();
        doReturn(new PayoutTransferClient.TransferResult("tr_it_sig_" + id.substring(0, 8), PayoutTransferClient.Status.PENDING, null))
                .when(transferClient).send(any());
        mockMvc.perform(post("/api/v1/payouts/{id}/send", id).header("Authorization", admin)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/webhooks/paymongo").contentType("application/json").header("Paymongo-Signature", "t=1,te=" + "0".repeat(64) + ",li=")
                .content("{\"data\":{\"attributes\":{\"type\":\"transfer.outward.successful\",\"data\":{\"attributes\":{\"transfer_id\":\"tr_it_sig_"
                        + id.substring(0, 8) + "\"}}}}}")).andExpect(status().isUnauthorized());
        transferWebhook("transfer.outward.successful", "tr_not_ours", null).andExpect(status().isOk());

        assertThat(payoutStatus(id)).isEqualTo("PROCESSING");
    }

    @Test
    void theWalletEndpoint_showsTheWalletToAnAdmin_andToNoOneElse() throws Exception {
        doReturn(new PayoutTransferClient.WalletInfo("PAYMONGO", "wallet_it", "deactivated", false, true, "basic", null, 2_500L, 100L,
                new PayoutTransferClient.WalletLimits(1_000_000L, 2L, 3L, 4L, 5L), new PayoutTransferClient.WalletUsage(0, 0, 0, 0),
                new PayoutTransferClient.WalletAccount("paymongo", "Test Platform Inc.", "0000000001", "PHP"),
                "2026-05-24T06:10:03.105Z", "2026-08-15T03:11:35.684Z", true)).when(transferClient).wallet();

        mockMvc.perform(get("/api/v1/payout-wallet").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("PAYMONGO")).andExpect(jsonPath("$.status").value("deactivated"))
                .andExpect(jsonPath("$.activated").value(false)).andExpect(jsonPath("$.livemode").value(false))
                .andExpect(jsonPath("$.isDefault").value(true)).andExpect(jsonPath("$.type").value("basic"))
                .andExpect(jsonPath("$.balance.available.amount").value(2500)).andExpect(jsonPath("$.balance.available.currency").value("PHP"))
                .andExpect(jsonPath("$.balance.pending.amount").value(100))
                .andExpect(jsonPath("$.limits.outwardDaily.amount").value(4)).andExpect(jsonPath("$.usage.inwardDaily.amount").value(0))
                .andExpect(jsonPath("$.account.accountNumber").value("0000000001")).andExpect(jsonPath("$.account.accountName").value("Test Platform Inc."))
                .andExpect(jsonPath("$.createdAt").value("2026-05-24T06:10:03.105Z")).andExpect(jsonPath("$.matchesConfiguredAccount").value(true));

        mockMvc.perform(get("/api/v1/payout-wallet").header("Authorization", customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payout-wallet")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFeeCheck_comparesTheSettingWithWhatPayMongoReallyCharged() throws Exception {
        doReturn(List.of(new com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample("pay_it", 10_500, "PHP", 1_868,
                105, 0, 8_527, "card", "visa", "US", 1_791_999_766L, false))).when(feeSampler).recentPaidPayments(5);

        mockMvc.perform(get("/api/v1/payment-gateway-fee/check").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("PAYMONGO"))
                .andExpect(jsonPath("$.samples[0].paymentId").value("pay_it"))
                .andExpect(jsonPath("$.samples[0].fee.amount").value(1868)).andExpect(jsonPath("$.samples[0].foreignFee.amount").value(105))
                .andExpect(jsonPath("$.samples[0].totalFee.amount").value(1973)).andExpect(jsonPath("$.samples[0].netAmount.amount").value(8527))
                .andExpect(jsonPath("$.samples[0].cardBrand").value("visa")).andExpect(jsonPath("$.samples[0].cardCountry").value("US"))
                .andExpect(jsonPath("$.samples[0].matchedRate").value("INTERNATIONAL_CARD"))
                .andExpect(jsonPath("$.suggestedSetting.percentage").value(4.5)).andExpect(jsonPath("$.suggestedSetting.flatAmount.amount").value(1500));
        mockMvc.perform(get("/api/v1/payment-gateway-fee/check").header("Authorization", customer)).andExpect(status().isForbidden());
    }

    @Test
    void theFinanceSummary_readsTheGatewaysRealFeeForAnOrderThatLacksIt_once() throws Exception {
        paidOrder();
        UUID orderId = orderIds.get(orderIds.size() - 1);
        jdbc.update("update orders set created_at = '2002-05-05T10:00:00Z', platform_fee_amount = 500 where id = ?", orderId);
        jdbc.update("insert into payments (id, order_id, gateway_ref, amount_amount, amount_currency, status, created_at) values (?, ?, 'pay_it_fee_1', 10000, 'PHP', 'COMPLETED', now())",
                UUID.randomUUID(), orderId);
        // PayMongo says it kept 1,868 + 105 of this payment (PHP 100.00 paid, 80.27 net)
        doReturn(List.of(new com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample("pay_it_fee_1", 10_000, "PHP", 1_868,
                105, 0, 8_027, "card", "visa", "US", 1_791_999_766L, false))).when(feeSampler).recentPaidPayments(100);
        try {
            mockMvc.perform(get("/api/v1/analytics/platform/finance?from=2002-05-01&to=2002-05-31").header("Authorization", admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders.count").value(1))
                    .andExpect(jsonPath("$.gatewayFees.charged.amount").value(1_973))
                    .andExpect(jsonPath("$.gatewayFees.borneByPlatform.amount").value(1_973))   // nobody else paid it
                    .andExpect(jsonPath("$.platform.feeIncome.amount").value(500))
                    .andExpect(jsonPath("$.platform.netIncome.amount").value(-1_473))   // the platform lost money on this order
                    .andExpect(jsonPath("$.gatewayFees.unknownFeeOrders").value(0));
            assertThat(jdbc.queryForObject("select gateway_fee_actual_amount from orders where id = ?", Long.class, orderId)).isEqualTo(1_973L);

            mockMvc.perform(get("/api/v1/analytics/platform/finance?from=2002-05-01&to=2002-05-31").header("Authorization", admin)).andExpect(status().isOk());
            verify(feeSampler, org.mockito.Mockito.times(1)).recentPaidPayments(100);   // already known: PayMongo is not asked again
        } finally {
            jdbc.update("delete from payments where order_id = ?", orderId);
        }
    }

    @Test
    void theFinanceSummary_stillAnswers_whenPayMongoCannotBeRead() throws Exception {
        doThrow(new com.junaldadlawan.event_ticketing_api.common.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "down"))
                .when(feeSampler).recentPaidPayments(100);

        mockMvc.perform(get("/api/v1/analytics/platform/finance?from=1990-01-01&to=1990-01-31").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.count").value(0));
    }

    @Test
    void thePayMongoTransferClientIsTheOneInUse() {
        assertThat(transferClient.requiresAccount()).isTrue();
    }
}
