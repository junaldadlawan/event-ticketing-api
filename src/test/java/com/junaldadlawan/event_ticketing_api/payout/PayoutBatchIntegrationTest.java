package com.junaldadlawan.event_ticketing_api.payout;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The batch payout screen's two endpoints on the real database, in March 2001 so the development data never mixes in. Organization A has event
 * EA1 (completed; orders 11,500 with customer-paid fees and 5,000 with organization-paid fees: eligible 14,050; plus one already in a payout and
 * one placed in April, both left out), EA2 (still to come, 7,000), EA3 (completed, a refund still pending: on hold) and EA4 (completed, orders in
 * PHP and USD: on hold). Organization B has EB1 (completed, 6,000) but no payout card.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PayoutBatchIntegrationTest {

    private static final String WINDOW = "periodStart=2001-03-01&periodEnd=2001-03-31";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private PayoutCardRepository payoutCardRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    private UUID orgA;
    private UUID orgB;
    private UUID ea1;
    private UUID ea2;
    private UUID ea3;
    private UUID ea4;
    private UUID eb1;
    private String admin;
    private String customer;

    @BeforeEach
    void setUp() {
        orgA = organization("A");
        orgB = organization("B");
        admin = token(Role.ADMIN);
        customer = token(Role.CUSTOMER);
        payoutCardRepository.save(PayoutCard.builder().organizationId(orgA).holderName("Demo Events Co.").brand("Visa").last4("4242").expMonth(12)
                .expYear(2099).paymentToken("tok_batch_test_1").primary(true).build());

        ea1 = event(orgA, "EA1 Completed", EventStatus.COMPLETED, -20);
        ea2 = event(orgA, "EA2 Upcoming", EventStatus.PUBLISHED, 20);
        ea3 = event(orgA, "EA3 Refund pending", EventStatus.COMPLETED, -19);
        ea4 = event(orgA, "EA4 Two currencies", EventStatus.COMPLETED, -18);
        eb1 = event(orgB, "EB1 Completed", EventStatus.COMPLETED, -20);

        order(orgA, ea1, 11_500, 1_000, GatewayFeePayer.CUSTOMER, 500, GatewayFeePayer.CUSTOMER, 500L, "PHP", "2001-03-10");   // share 10,000
        order(orgA, ea1, 5_000, 500, GatewayFeePayer.ORGANIZATION, 300, GatewayFeePayer.ORGANIZATION, 450L, "PHP", "2001-03-11");   // share 4,050
        order(orgA, ea1, 9_999, 0, null, 0, null, null, "PHP", "2001-04-02");   // placed in April: outside the window
        UUID settled = order(orgA, ea1, 8_888, 0, null, 0, null, null, "PHP", "2001-03-12");   // already in a payout
        order(orgA, ea2, 7_000, 0, null, 0, null, null, "PHP", "2001-03-13");
        UUID held = order(orgA, ea3, 2_000, 0, null, 0, null, null, "PHP", "2001-03-14");
        refundRepository.save(Refund.builder().orderId(held).amount(Money.builder().amount(500).currency("PHP").build()).reason("pending")
                .initiatedBy(UUID.randomUUID()).status(RefundStatus.PENDING).build());
        order(orgA, ea4, 1_000, 0, null, 0, null, null, "PHP", "2001-03-15");
        order(orgA, ea4, 1_000, 0, null, 0, null, null, "USD", "2001-03-16");
        order(orgB, eb1, 6_000, 0, null, 0, null, null, "PHP", "2001-03-17");

        UUID earlier = UUID.randomUUID();
        jdbc.update("insert into payouts (id, organization_id, gross_amount, gross_currency, fees_amount, fees_currency, net_amount, net_currency, period_start, period_end, status, created_at, transfer_attempts) "
                + "values (?, ?, 8888, 'PHP', 0, 'PHP', 8888, 'PHP', '2001-03-01', '2001-03-31', 'PAID', '2001-03-20T10:00:00Z', 0)", earlier, orgA);
        jdbc.update("update orders set payout_id = ? where id = ?", earlier, settled);
    }

    @AfterEach
    void tearDown() {
        orgIds.forEach(id -> {
            jdbc.update("update orders set payout_id = null where payee_id = ?", id);
            jdbc.update("delete from payouts where organization_id = ?", id);
            jdbc.update("delete from payout_cards where organization_id = ?", id);
        });
        orderIds.forEach(id -> {
            jdbc.update("delete from refunds where order_id = ?", id);
            jdbc.update("delete from tickets where order_id = ?", id);
        });
        orderIds.forEach(orderRepository::deleteById);
        ticketTypeIds.forEach(ticketTypeRepository::deleteById);
        eventIds.forEach(eventRepository::deleteById);
        orgIds.forEach(organizationRepository::deleteById);
        actorIds.forEach(id -> jdbc.update("delete from audit_log_entries where actor_id = ?", id));
    }

    // ---- fixtures ----

    private UUID organization(String name) {
        Organization saved = organizationRepository.save(Organization.builder().name("Batch Test Org " + name + " " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(UUID.randomUUID()).documents(List.of()).build());
        orgIds.add(saved.getId());
        return saved.getId();
    }

    private String token(Role role) {
        UUID id = UUID.randomUUID();
        actorIds.add(id);
        return "Bearer " + jwtService.generateAccessToken(User.builder().id(id).email("batch-it-" + UUID.randomUUID() + "@test.local").role(role).build());
    }

    private UUID event(UUID org, String title, EventStatus status, int endsInDays) {
        Instant end = Instant.now().plus(endsInDays, ChronoUnit.DAYS);
        Event saved = eventRepository.save(Event.builder().organizationId(org).title(title).description("d").category("music").status(status)
                .ticketPrefix(UUID.randomUUID().toString().substring(0, 3).toUpperCase()).startAt(end.minus(2, ChronoUnit.HOURS)).endAt(end).timezone("UTC").build());
        eventIds.add(saved.getId());
        return saved.getId();
    }

    private UUID order(UUID org, UUID event, long total, long platformFee, GatewayFeePayer platformPayer, long gatewayFee, GatewayFeePayer gatewayPayer,
                       Long applied, String currency, String day) {
        Order saved = orderRepository.save(Order.builder().buyerId(UUID.randomUUID()).payeeType(PayeeType.ORGANIZATION).payeeId(org).status(OrderStatus.PAID)
                .total(Money.builder().amount(total).currency(currency).build()).platformFeeAmount(platformFee).platformFeePayer(platformPayer)
                .gatewayFeeAmount(gatewayFee).gatewayFeePayer(gatewayPayer).gatewayFeeAppliedAmount(applied).createdBy("batch-it").build());
        jdbc.update("update orders set created_at = ?::timestamptz where id = ?", day + "T10:00:00Z", saved.getId());
        orderIds.add(saved.getId());
        TicketType type = ticketTypeRepository.save(TicketType.builder().eventId(event).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(1000L).currency(currency).build()).quantityTotal(10).quantityAvailable(5)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS)).maxPerOrder(10).build());
        ticketTypeIds.add(type.getId());
        UUID ticketId = UUID.randomUUID();
        ticketRepository.save(Ticket.builder().id(ticketId).orderId(saved.getId()).eventId(event).ticketTypeId(type.getId()).seatId(null)
                .ownerId(UUID.randomUUID()).ticketNumber("B-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .credential("cred-" + ticketId).credentialVersion(0).status(TicketStatus.VALID).build());
        return saved.getId();
    }

    private ResultActions eligible(String caller, String query) throws Exception {
        var request = get("/api/v1/payouts/eligible" + (query.isEmpty() ? "" : "?" + query));
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    private ResultActions batch(String caller, String body) throws Exception {
        var request = post("/api/v1/payouts/batch").contentType("application/json").content(body);
        return mockMvc.perform(caller == null ? request : request.header("Authorization", caller));
    }

    private String lines(UUID org, UUID... events) {
        StringBuilder items = new StringBuilder();
        for (UUID event : events) {
            items.append(items.length() == 0 ? "" : ",").append("{\"organizationId\":\"").append(org).append("\",\"eventId\":\"").append(event).append("\"}");
        }
        return items.toString();
    }

    private String body(String items) {
        return "{\"periodStart\":\"2001-03-01\",\"periodEnd\":\"2001-03-31\",\"items\":[" + items + "]}";
    }

    // ---- GET /payouts/eligible ----

    @Test
    void theCompletedEventsOfAnOrganization_areListedWithWhatAPayoutWouldPay_andWhatIsOnHold() throws Exception {
        eligible(admin, WINDOW + "&organizationId=" + orgA + "&eventScope=COMPLETED").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')].eventTitle").value("EA1 Completed"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')].eligible.amount").value(14_050))   // 10,000 + 4,050
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')].eligible.currency").value("PHP"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')].status").value("READY"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea3 + "')].status").value("ON_HOLD"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea3 + "')].holdReason").value("Pending refunds"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea3 + "')].eligible.amount").value(2_000))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea4 + "')].status").value("ON_HOLD"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea4 + "')].holdReason").value("More than one currency"))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')].organizationId").value(orgA.toString()))
                .andExpect(jsonPath("$.summary.eligibleOrganizations").value(1))
                .andExpect(jsonPath("$.summary.eligibleAmount.amount").value(14_050))
                .andExpect(jsonPath("$.summary.onHold.amount").value(2_000));   // the two-currency line has no single amount to add
    }

    @Test
    void allEventsAddTheOnesStillToCome_andNoOrganizationFilterAddsTheOthers() throws Exception {
        eligible(admin, WINDOW + "&organizationId=" + orgA + "&eventScope=ALL").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea2 + "')].eligible.amount").value(7_000))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + ea2 + "')].status").value("READY"))
                .andExpect(jsonPath("$.summary.eligibleAmount.amount").value(21_050));
        eligible(admin, WINDOW).andExpect(status().isOk())   // eventScope defaults to COMPLETED; every organization
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + eb1 + "')].eligible.amount").value(6_000))
                .andExpect(jsonPath("$.items[?(@.eventId=='" + eb1 + "')].organizationId").value(orgB.toString()))
                .andExpect(jsonPath("$.summary.eligibleOrganizations").value(2))
                .andExpect(jsonPath("$.summary.eligibleAmount.amount").value(20_050));
        eligible(admin, WINDOW + "&organizationId=" + orgB).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void aBadPeriodIsRefused_andOnlyAnAdminSeesTheList() throws Exception {
        eligible(admin, "").andExpect(status().isBadRequest());
        eligible(admin, "periodStart=2001-03-01").andExpect(status().isBadRequest());
        eligible(admin, "periodStart=2001-03-31&periodEnd=2001-03-01").andExpect(status().isBadRequest());
        eligible(admin, "periodStart=2000-01-01&periodEnd=2001-03-31").andExpect(status().isBadRequest());   // over 366 days
        eligible(admin, "periodStart=2001-03-01&periodEnd=2999-01-01").andExpect(status().isBadRequest());   // the future
        eligible(admin, WINDOW + "&eventScope=SOME").andExpect(status().isBadRequest());
        eligible(admin, WINDOW + "&organizationId=" + UUID.randomUUID()).andExpect(status().isNotFound());
        eligible(customer, WINDOW).andExpect(status().isForbidden());
        eligible(null, WINDOW).andExpect(status().isUnauthorized());
    }

    // ---- POST /payouts/batch ----

    @Test
    void theChosenLineBecomesAPayoutOfThatEvent_paysWhatTheListShowed_andIsNotListedAgain() throws Exception {
        batch(admin, body(lines(orgA, ea1))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].organizationId").value(orgA.toString())).andExpect(jsonPath("$[0].eventId").value(ea1.toString()))
                .andExpect(jsonPath("$[0].eventTitle").value("EA1 Completed")).andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].net.amount").value(14_050)).andExpect(jsonPath("$[0].gross.amount").value(16_500))
                .andExpect(jsonPath("$[0].fees.amount").value(2_450));

        // only that event's due orders were settled
        assertThat(jdbc.queryForObject("select count(*) from orders where payee_id = ? and payout_id is not null", Integer.class, orgA)).isEqualTo(3);   // + the earlier one
        assertThat(jdbc.queryForObject("select count(*) from payouts where organization_id = ? and event_id = ?", Integer.class, orgA, ea1)).isEqualTo(1);
        batch(admin, body(lines(orgA, ea1))).andExpect(status().isConflict());   // nothing is due any more: never paid out twice
        eligible(admin, WINDOW + "&organizationId=" + orgA).andExpect(jsonPath("$.items[?(@.eventId=='" + ea1 + "')]").isEmpty());
        mockMvc.perform(get("/api/v1/organizations/{id}/payouts", orgA).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventId=='" + ea1 + "')].eventTitle").value("EA1 Completed"));
    }

    @Test
    void aBatchIsAllOrNothing_andALineOnHoldOrWithoutDestinationOrOrders_isRefused() throws Exception {
        batch(admin, body(lines(orgA, ea1) + "," + lines(orgB, eb1))).andExpect(status().isConflict());   // B has no payout card
        assertThat(jdbc.queryForObject("select count(*) from payouts where organization_id in (?, ?) and event_id is not null", Integer.class, orgA, orgB)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders where payee_id = ? and payout_id is null and status = 'PAID'", Integer.class, orgA)).isEqualTo(7);   // A's orders are untouched

        batch(admin, body(lines(orgA, ea3))).andExpect(status().isConflict());   // a refund is still pending
        batch(admin, body(lines(orgA, ea4))).andExpect(status().isConflict());   // PHP and USD in one payout
        batch(admin, body(lines(orgA, ea2)).replace("2001-03-31", "2001-03-09")).andExpect(status().isConflict());   // nothing placed by then
    }

    @Test
    void severalLinesBecomeSeveralPayouts_inTheOrderGiven() throws Exception {
        batch(admin, body(lines(orgA, ea2, ea1))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventId").value(ea2.toString())).andExpect(jsonPath("$[0].net.amount").value(7_000))
                .andExpect(jsonPath("$[1].eventId").value(ea1.toString())).andExpect(jsonPath("$[1].net.amount").value(14_050));
    }

    @Test
    void aBadBatchIsRefused_andOnlyAnAdminMayCreateOne() throws Exception {
        batch(admin, body("")).andExpect(status().isBadRequest());
        batch(admin, "{\"periodStart\":\"2001-03-01\",\"items\":[" + lines(orgA, ea1) + "]}").andExpect(status().isBadRequest());
        batch(admin, body(lines(orgA, ea1, ea1))).andExpect(status().isBadRequest());   // the same line twice
        batch(admin, body("{\"organizationId\":\"" + orgA + "\",\"eventId\":null}")).andExpect(status().isBadRequest());
        batch(admin, body(lines(orgA, ea1)).replace("2001-03-31", "2999-01-01")).andExpect(status().isBadRequest());
        batch(admin, body(lines(orgA, eb1))).andExpect(status().isNotFound());   // an event that is not the organization's
        batch(admin, body(lines(UUID.randomUUID(), ea1))).andExpect(status().isNotFound());
        batch(customer, body(lines(orgA, ea1))).andExpect(status().isForbidden());
        batch(null, body(lines(orgA, ea1))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from payouts where organization_id = ? and event_id is not null", Integer.class, orgA)).isZero();
    }
}
