package com.junaldadlawan.event_ticketing_api.analytics;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeeCardOrigin;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin's and an organization's money summaries on the real database, with a scenario worked out by hand. Everything is placed in March
 * 2001, so the real orders of the development database never mix in. Organization A has: O1 (customer pays a 10% platform fee and a gateway
 * fee), O2 (organization bears both, foreign card: the foreign fee applies), O3 (platform bears both), O4 (partly refunded), O5 (fully
 * refunded); organization B has O6 (no fees) and O7 (USD); O8 falls in April. Two payouts of A (one PAID, one SCHEDULED) and two of B exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinanceSummaryIntegrationTest {

    private static final String WINDOW = "from=2001-03-01&to=2001-03-31";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private PayoutRepository payoutRepository;
    @Autowired
    private PayoutCardRepository payoutCardRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    private UUID orgA;
    private UUID orgB;
    private String admin;
    private String ownerA;
    private String ownerB;
    private String organizerA;
    private String customer;

    @BeforeEach
    void setUp() {
        orgA = organization();
        orgB = organization();
        admin = token(Role.ADMIN, UUID.randomUUID());
        ownerA = memberToken(orgA, OrganizationRole.OWNER);
        ownerB = memberToken(orgB, OrganizationRole.OWNER);
        organizerA = memberToken(orgA, OrganizationRole.ORGANIZER);
        customer = token(Role.CUSTOMER, UUID.randomUUID());

        UUID o1 = order(orgA, 11_500, OrderStatus.PAID, 1_000, GatewayFeePayer.CUSTOMER, 500, GatewayFeePayer.CUSTOMER, 500L, null, "PHP", "2001-03-05");
        refund(o1, 500, RefundStatus.FAILED);   // a failed refund moves no money: it must not count
        UUID o2 = order(orgA, 5_000, OrderStatus.PAID, 500, GatewayFeePayer.ORGANIZATION, 300, GatewayFeePayer.ORGANIZATION, 450L, GatewayFeeCardOrigin.FOREIGN, "PHP", "2001-03-06");
        order(orgA, 4_000, OrderStatus.PAID, 400, GatewayFeePayer.PLATFORM, 200, GatewayFeePayer.PLATFORM, 200L, null, "PHP", "2001-03-07");
        UUID o4 = order(orgA, 10_000, OrderStatus.PARTIALLY_REFUNDED, 1_000, GatewayFeePayer.CUSTOMER, 0, null, null, null, "PHP", "2001-03-08");
        refund(o4, 4_000, RefundStatus.COMPLETED);
        UUID o5 = order(orgA, 3_400, OrderStatus.REFUNDED, 300, GatewayFeePayer.CUSTOMER, 100, GatewayFeePayer.CUSTOMER, 100L, null, "PHP", "2001-03-09");
        refund(o5, 3_400, RefundStatus.COMPLETED);
        order(orgB, 7_000, OrderStatus.PAID, 0, null, 0, null, null, null, "PHP", "2001-03-10");
        order(orgB, 2_000, OrderStatus.PAID, 0, null, 0, null, null, null, "USD", "2001-03-11");
        order(orgA, 9_999, OrderStatus.PAID, 0, null, 0, null, null, null, "PHP", "2001-04-02");   // outside the window

        UUID paid = payout(orgA, PayoutStatus.PAID, 4_050);
        UUID scheduled = payout(orgA, PayoutStatus.SCHEDULED, 5_000);
        payout(orgB, PayoutStatus.PROCESSING, 123);
        payout(orgB, PayoutStatus.FAILED, 77);
        jdbc.update("update orders set payout_id = ? where id = ?", paid, o2);
        jdbc.update("update orders set payout_id = ? where id = ?", scheduled, o4);
    }

    @AfterEach
    void tearDown() {
        orgIds.forEach(id -> {
            jdbc.update("update orders set payout_id = null where payee_id = ?", id);
            jdbc.update("delete from payouts where organization_id = ?", id);
            jdbc.update("delete from payout_cards where organization_id = ?", id);
        });
        orderIds.forEach(id -> jdbc.update("delete from refunds where order_id = ?", id));
        orderIds.forEach(orderRepository::deleteById);
        members.forEach(member -> organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        orgIds.forEach(organizationRepository::deleteById);
        actorIds.forEach(id -> jdbc.update("delete from audit_log_entries where actor_id = ?", id));
    }

    // ---- fixtures ----

    private UUID organization() {
        Organization saved = organizationRepository.save(Organization.builder().name("Finance Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private String token(Role role, UUID id) {
        actorIds.add(id);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id).email("finance-it-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private String memberToken(UUID org, OrganizationRole role) {
        UUID userId = UUID.randomUUID();
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(org).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
        return token(Role.CUSTOMER, userId);
    }

    private UUID order(UUID org, long total, OrderStatus status, long platformFee, GatewayFeePayer platformPayer, long gatewayFee,
                       GatewayFeePayer gatewayPayer, Long applied, GatewayFeeCardOrigin origin, String currency, String day) {
        Order saved = orderRepository.save(Order.builder().buyerId(UUID.randomUUID()).payeeType(PayeeType.ORGANIZATION).payeeId(org).status(status)
                .total(Money.builder().amount(total).currency(currency).build())
                .platformFeeAmount(platformFee).platformFeePayer(platformPayer)
                .gatewayFeeAmount(gatewayFee).gatewayFeePayer(gatewayPayer).gatewayFeeAppliedAmount(applied).gatewayFeeCardOrigin(origin)
                .createdBy("finance-it").build());
        jdbc.update("update orders set created_at = ?::timestamptz where id = ?", day + "T10:00:00Z", saved.getId());
        orderIds.add(saved.getId());
        return saved.getId();
    }

    private void refund(UUID orderId, long amount, RefundStatus status) {
        refundRepository.save(Refund.builder().orderId(orderId).amount(Money.builder().amount(amount).currency("PHP").build())
                .reason("finance test").initiatedBy(UUID.randomUUID()).status(status).build());
    }

    private UUID payout(UUID org, PayoutStatus status, long net) {
        Money money = Money.builder().amount(net).currency("PHP").build();
        Payout saved = payoutRepository.save(Payout.builder().organizationId(org).gross(money).fees(Money.builder().amount(0).currency("PHP").build())
                .net(money).periodStart(LocalDate.of(2001, 3, 1)).periodEnd(LocalDate.of(2001, 3, 31)).status(status).build());
        jdbc.update("update payouts set created_at = ?::timestamptz where id = ?", "2001-03-20T10:00:00Z", saved.getId());
        return saved.getId();
    }

    private ResultActions platform(String caller, String query) throws Exception {
        var request = get("/api/v1/analytics/platform/finance" + (query.isEmpty() ? "" : "?" + query));
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    private ResultActions organization(UUID org, String caller, String query) throws Exception {
        var request = get("/api/v1/organizations/{id}/finance" + (query.isEmpty() ? "" : "?" + query), org);
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    // ---- the admin's summary ----

    @Test
    void theAdminSummary_matchesTheHandComputedNumbers() throws Exception {
        platform(admin, WINDOW).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2001-03-01")).andExpect(jsonPath("$.to").value("2001-03-31")).andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.otherCurrencies[0]").value("USD")).andExpect(jsonPath("$.otherCurrencies.length()").value(1))
                .andExpect(jsonPath("$.orders.count").value(6)).andExpect(jsonPath("$.orders.refunded").value(2))
                .andExpect(jsonPath("$.sales.gross.amount").value(40_900)).andExpect(jsonPath("$.sales.gross.currency").value("PHP"))
                .andExpect(jsonPath("$.sales.refunds.amount").value(7_400)).andExpect(jsonPath("$.sales.net.amount").value(33_500))
                .andExpect(jsonPath("$.platform.feeIncome.amount").value(2_500))
                .andExpect(jsonPath("$.platform.gatewayCostBorne.amount").value(300))   // O3 (platform pays) 200 + O5 (fully refunded) 100
                .andExpect(jsonPath("$.platform.netIncome.amount").value(2_200))
                .andExpect(jsonPath("$.gatewayFees.charged.amount").value(1_250)).andExpect(jsonPath("$.gatewayFees.paidByCustomer.amount").value(600))
                .andExpect(jsonPath("$.gatewayFees.borneByOrganization.amount").value(450)).andExpect(jsonPath("$.gatewayFees.borneByPlatform.amount").value(300))
                .andExpect(jsonPath("$.gatewayFees.foreignCardOrders").value(1))
                .andExpect(jsonPath("$.gatewayFees.unknownFeeOrders").value(2))   // O4 and O6 carry no fee information at all
                .andExpect(jsonPath("$.organizers.share.amount").value(30_050)).andExpect(jsonPath("$.organizers.paidOut.amount").value(4_050))
                .andExpect(jsonPath("$.organizers.inPayouts.amount").value(5_000)).andExpect(jsonPath("$.organizers.owedNotYetInPayout.amount").value(21_000))
                .andExpect(jsonPath("$.payouts.scheduled.count").value(1)).andExpect(jsonPath("$.payouts.scheduled.net.amount").value(5_000))
                .andExpect(jsonPath("$.payouts.processing.count").value(1)).andExpect(jsonPath("$.payouts.processing.net.amount").value(123))
                .andExpect(jsonPath("$.payouts.paid.count").value(1)).andExpect(jsonPath("$.payouts.paid.net.amount").value(4_050))
                .andExpect(jsonPath("$.payouts.failed.count").value(1)).andExpect(jsonPath("$.payouts.failed.net.amount").value(77));
    }

    @Test
    void whatTheGatewayReallyKept_replacesTheEstimate_andTheAdminBearsWhatNobodyElsePaid() throws Exception {
        // O6 (no fee recorded at all, like an order from before gateway fees were stored): PayMongo really kept 1,973 (a US card)
        jdbc.update("update orders set gateway_fee_actual_amount = 1973 where payee_id = ? and total_amount = 7000 and created_by = 'finance-it'", orgB);
        // O1 (the customer was charged a 500 fee): the gateway really kept only 300, so the platform keeps the 200 surplus
        jdbc.update("update orders set gateway_fee_actual_amount = 300 where payee_id = ? and total_amount = 11500 and created_by = 'finance-it'", orgA);

        platform(admin, WINDOW).andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayFees.charged.amount").value(3_023))   // O1 300 + O2 450 + O3 200 + O5 100 + O6 1,973
                .andExpect(jsonPath("$.gatewayFees.paidByCustomer.amount").value(600))
                .andExpect(jsonPath("$.gatewayFees.borneByOrganization.amount").value(450))
                .andExpect(jsonPath("$.gatewayFees.borneByPlatform.amount").value(2_073))   // O1 -200 (surplus) + O3 200 + O5 100 + O6 1,973
                .andExpect(jsonPath("$.platform.gatewayCostBorne.amount").value(2_073))
                .andExpect(jsonPath("$.platform.netIncome.amount").value(427))   // 2,500 fee income - 2,073
                .andExpect(jsonPath("$.gatewayFees.unknownFeeOrders").value(1));   // only O4 is still unknown
        // an organization's own figures never change with what the gateway kept: its deductions are what comes off its share
        organization(orgA, ownerA, WINDOW).andExpect(jsonPath("$.deductions.gatewayFee.amount").value(950)).andExpect(jsonPath("$.income.amount").value(23_050));
    }

    @Test
    void theCurrencyAndTheDatesDecideWhatCounts() throws Exception {
        platform(admin, WINDOW + "&currency=usd").andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.orders.count").value(1)).andExpect(jsonPath("$.sales.gross.amount").value(2_000))
                .andExpect(jsonPath("$.otherCurrencies[0]").value("PHP"));
        platform(admin, "from=2001-04-01&to=2001-04-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.count").value(1)).andExpect(jsonPath("$.sales.gross.amount").value(9_999));
        platform(admin, "from=2001-03-05&to=2001-03-05").andExpect(status().isOk())   // one day, both ends included
                .andExpect(jsonPath("$.orders.count").value(1)).andExpect(jsonPath("$.sales.gross.amount").value(11_500));
        platform(admin, "from=1990-01-01&to=1990-01-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.count").value(0)).andExpect(jsonPath("$.sales.gross.amount").value(0))
                .andExpect(jsonPath("$.platform.netIncome.amount").value(0)).andExpect(jsonPath("$.payouts.paid.count").value(0));
        platform(admin, "").andExpect(status().isOk());   // the default period: the last 30 days
    }

    @Test
    void aBadPeriodOrCurrencyIsRefused() throws Exception {
        platform(admin, "from=2001-03-31&to=2001-03-01").andExpect(status().isBadRequest());
        platform(admin, "from=2000-01-01&to=2001-03-31").andExpect(status().isBadRequest());   // over 366 days
        platform(admin, WINDOW + "&currency=PESO").andExpect(status().isBadRequest());
        platform(admin, "from=not-a-date").andExpect(status().isBadRequest());
    }

    @Test
    void onlyAnAdminSeesThePlatformSummary() throws Exception {
        platform(ownerA, WINDOW).andExpect(status().isForbidden());
        platform(customer, WINDOW).andExpect(status().isForbidden());
        platform(null, WINDOW).andExpect(status().isUnauthorized());
    }

    // ---- an organization's own summary ----

    @Test
    void theOwnerSeesOnlyItsOwnMoney_asWorkedOutByHand() throws Exception {
        organization(orgA, ownerA, WINDOW).andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(orgA.toString())).andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.otherCurrencies.length()").value(0))
                .andExpect(jsonPath("$.orders").value(5))
                .andExpect(jsonPath("$.sales.gross.amount").value(33_900)).andExpect(jsonPath("$.sales.refunds.amount").value(7_400))
                .andExpect(jsonPath("$.sales.net.amount").value(26_500))
                .andExpect(jsonPath("$.deductions.platformFee.amount").value(2_500)).andExpect(jsonPath("$.deductions.gatewayFee.amount").value(950))
                .andExpect(jsonPath("$.deductions.total.amount").value(3_450))
                .andExpect(jsonPath("$.income.amount").value(23_050))   // 26,500 - 3,450
                .andExpect(jsonPath("$.paidOut.amount").value(4_050)).andExpect(jsonPath("$.inPayouts.amount").value(5_000))
                .andExpect(jsonPath("$.owedNotYetInPayout.amount").value(14_000))
                .andExpect(jsonPath("$.payouts.scheduled.count").value(1)).andExpect(jsonPath("$.payouts.scheduled.net.amount").value(5_000))
                .andExpect(jsonPath("$.payouts.paid.net.amount").value(4_050)).andExpect(jsonPath("$.payouts.processing.count").value(0))
                .andExpect(jsonPath("$.payouts.failed.count").value(0))
                // nothing of the platform's own figures, and nothing of the other organization
                .andExpect(jsonPath("$.platform").doesNotExist()).andExpect(jsonPath("$.gatewayFees").doesNotExist());
        organization(orgB, ownerB, WINDOW).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(1)).andExpect(jsonPath("$.sales.gross.amount").value(7_000))
                .andExpect(jsonPath("$.income.amount").value(7_000)).andExpect(jsonPath("$.owedNotYetInPayout.amount").value(7_000))
                .andExpect(jsonPath("$.otherCurrencies[0]").value("USD"))
                .andExpect(jsonPath("$.payouts.processing.net.amount").value(123)).andExpect(jsonPath("$.payouts.failed.net.amount").value(77))
                .andExpect(jsonPath("$.payouts.paid.count").value(0));
    }

    @Test
    void theIncomeShownIsTheNetTheNextPayoutPays() throws Exception {
        payoutCardRepository.save(PayoutCard.builder().organizationId(orgA).holderName("Demo Events Co.").brand("Visa").last4("4242").expMonth(12)
                .expYear(2099).paymentToken("tok_finance_test_1").primary(true).build());
        organization(orgA, ownerA, WINDOW).andExpect(jsonPath("$.owedNotYetInPayout.amount").value(14_000));

        mockMvc.perform(post("/api/v1/organizations/{id}/payouts", orgA).header("Authorization", admin).contentType("application/json")
                        .content("{\"periodStart\":\"2001-03-01\",\"periodEnd\":\"2001-03-31\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.net.amount").value(14_000));   // O1 10,000 + O3 4,000

        organization(orgA, ownerA, WINDOW).andExpect(jsonPath("$.owedNotYetInPayout.amount").value(0))
                .andExpect(jsonPath("$.inPayouts.amount").value(19_000)).andExpect(jsonPath("$.income.amount").value(23_050));
    }

    @Test
    void onlyTheOwnerOrAnAdminSeesAnOrganizationsMoney() throws Exception {
        organization(orgA, admin, WINDOW).andExpect(status().isOk()).andExpect(jsonPath("$.income.amount").value(23_050));
        organization(orgA, organizerA, WINDOW).andExpect(status().isForbidden());   // a member, but not the owner
        organization(orgA, ownerB, WINDOW).andExpect(status().isForbidden());   // another organization's owner
        organization(orgA, customer, WINDOW).andExpect(status().isForbidden());
        organization(orgA, null, WINDOW).andExpect(status().isUnauthorized());
        organization(UUID.randomUUID(), admin, WINDOW).andExpect(status().isNotFound());
        organization(orgA, ownerA, "from=2001-03-31&to=2001-03-01").andExpect(status().isBadRequest());
    }
}
