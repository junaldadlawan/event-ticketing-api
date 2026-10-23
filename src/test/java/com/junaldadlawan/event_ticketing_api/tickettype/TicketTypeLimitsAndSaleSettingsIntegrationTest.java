package com.junaldadlawan.event_ticketing_api.tickettype;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
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
import com.junaldadlawan.event_ticketing_api.platformsetting.entity.TicketSaleSettings;
import com.junaldadlawan.event_ticketing_api.platformsetting.repository.TicketSaleSettingsRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-TICKET-LIMIT-001, BR-TICKET-GATE-001 and BR-ADMIN-SETTINGS-002. */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTypeLimitsAndSaleSettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketSaleSettingsRepository settingsRepository;

    private final List<UUID> ticketTypeIds = new ArrayList<>();
    private final List<UUID> orderIds = new ArrayList<>();
    private User owner;
    private User buyer;
    private User admin;
    private UUID orgId;
    private UUID eventId;
    private Instant eventStart;

    @BeforeEach
    void setUp() {
        owner = user(Role.CUSTOMER);
        buyer = user(Role.CUSTOMER);
        admin = user(Role.ADMIN);
        orgId = organizationRepository.save(Organization.builder().name("Limits Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).ownerId(owner.getId()).documents(List.of()).build()).getId();
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(orgId).build();
        member.getRoles().add(OrganizationRole.OWNER);
        organizationMemberRepository.save(member);
        eventStart = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        eventId = eventRepository.save(Event.builder().organizationId(orgId).title("Limits Test Event").description("desc")
                .category("music").status(EventStatus.PUBLISHED).ticketPrefix("L" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(eventStart).endAt(eventStart.plus(2, ChronoUnit.HOURS)).timezone("UTC").build()).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from cart_items where cart_id in (select id from carts where buyer_id = ?)", buyer.getId());
        jdbc.update("delete from carts where buyer_id = ?", buyer.getId());
        jdbc.update("delete from checkout_idempotency_keys where buyer_id = ?", buyer.getId());
        jdbc.update("delete from cart_activity where event_id = ?", eventId);
        orderIds.forEach(id -> jdbc.update("delete from tickets where order_id = ?", id));
        orderIds.forEach(orderRepository::deleteById);
        ticketTypeIds.forEach(id -> jdbc.update("delete from ticket_types where id = ?", id));
        jdbc.update("delete from audit_log_entries where event_id = ?", eventId);
        eventRepository.deleteById(eventId);
        organizationMemberRepository.findByUserIdAndOrganizationId(owner.getId(), orgId).ifPresent(organizationMemberRepository::delete);
        organizationRepository.deleteById(orgId);
    }

    @Test
    void maxPerOrderAbovePerBuyerLimit_isRefused_onCreateAndUpdate() throws Exception {
        createType(",\"maxPerOrder\":5,\"maxPerUser\":3").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Max per order can't be more than the per-buyer limit"));
        UUID id = idOf(createType(",\"maxPerUser\":3").andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxPerUser").value(3))
                .andExpect(jsonPath("$.maxPerOrder").value(3)));   // the default per-order limit follows the lower per-buyer limit
        edit(id, "{\"maxPerOrder\":4}").andExpect(status().isBadRequest());
        edit(id, "{\"maxPerUser\":null,\"maxPerOrder\":8}").andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPerUser").doesNotExist()).andExpect(jsonPath("$.maxPerOrder").value(8));
        createType(",\"maxPerUser\":0").andExpect(status().isBadRequest());
    }

    @Test
    void buyingPastThePerBuyerLimit_acrossTwoOrders_is422_inTheCartAndAtCheckout() throws Exception {
        UUID typeId = idOf(createType(",\"maxPerUser\":4,\"maxPerOrder\":4").andExpect(status().isCreated()));
        paidOrder(typeId, 2);   // a first order of 2

        UUID cartId = idOf(as(post("/api/v1/carts"), buyer).andExpect(status().isCreated()));
        addToCart(cartId, typeId, 3).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You can buy at most 4 General tickets; you already have 2."));
        addToCart(cartId, typeId, 2).andExpect(status().isCreated());
        addToCart(cartId, typeId, 1).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You can buy at most 4 General tickets; you already have 4."));

        // a second order is paid elsewhere while these 2 sit in the cart: checkout now refuses before charging anything
        paidOrder(typeId, 1);
        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId).header("Authorization", bearer(buyer))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You can buy at most 4 General tickets; you already have 3."));
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_id = ?", Long.class, buyer.getId())).isEqualTo(2);
    }

    @Test
    void ticketTypesNoLongerHaveAGateTime() throws Exception {
        UUID id = idOf(createType(",\"gateOpensMinutesBefore\":45").andExpect(status().isCreated()));   // ignored: it lives on the event now
        as(get("/api/v1/ticket-types/{id}", id), buyer).andExpect(jsonPath("$.gateOpensMinutesBefore").doesNotExist());
    }

    @Test
    void onlyAdminsChangeTheSaleTimingRules_whichApplyToNewAndEditedWindowsOnly() throws Exception {
        TicketSaleSettings original = settingsRepository.findById(TicketSaleSettings.ROW_ID).orElseThrow();
        int originalClose = original.getSalesCloseMinutesBeforeEvent();
        int originalMin = original.getMinSaleDurationMinutes();
        try {
            settings(admin, 60, 60).andExpect(status().isOk());   // start from the defaults whatever the dev database holds
            Instant eventEnd = eventStart.plus(2, ChronoUnit.HOURS);
            // sales may run on after the event has started: this window ends 30 minutes in, 90 minutes before the end
            UUID existing = idOf(createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventEnd.minus(90, ChronoUnit.MINUTES)))
                    .andExpect(status().isCreated()));
            createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventEnd.minus(30, ChronoUnit.MINUTES)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Ticket sales must end at least 1 hour before the event ends (by "
                            + eventEnd.minus(60, ChronoUnit.MINUTES) + ")"));

            as(get("/api/v1/platform-settings/ticket-sales"), owner).andExpect(status().isOk())
                    .andExpect(jsonPath("$.salesCloseMinutesBeforeEvent").value(60));
            mockMvc.perform(get("/api/v1/platform-settings/ticket-sales")).andExpect(status().isUnauthorized());
            settings(owner, 120, 30).andExpect(status().isForbidden());
            settings(admin, 120, 30).andExpect(status().isOk())
                    .andExpect(jsonPath("$.salesCloseMinutesBeforeEvent").value(120)).andExpect(jsonPath("$.minSaleDurationMinutes").value(30));
            settings(admin, 10081, 30).andExpect(status().isBadRequest());
            String logged = jdbc.queryForObject("select details::text from audit_log_entries where action = 'ticket_sale_settings.updated'"
                    + " order by created_at desc limit 1", String.class);
            assertThat(logged).contains("\"field\": \"salesCloseMinutesBeforeEvent\"").contains("\"to\": 120");

            createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventEnd.minus(90, ChronoUnit.MINUTES)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("Ticket sales must end at least 2 hours before the event ends")));
            // 45 minutes is now long enough (minimum 30)
            createType(window(eventEnd.minus(5, ChronoUnit.HOURS), eventEnd.minus(255, ChronoUnit.MINUTES))).andExpect(status().isCreated());

            // the existing type keeps its window: editing something else is fine, editing the window applies the new rule
            edit(existing, "{\"name\":\"Still fine\"}").andExpect(status().isOk());
            edit(existing, "{\"saleEndAt\":\"" + eventEnd.minus(100, ChronoUnit.MINUTES) + "\"}").andExpect(status().isBadRequest());

            settings(admin, 60, 120).andExpect(status().isOk());
            createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventStart.minus(9, ChronoUnit.HOURS)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Ticket sales must run for at least 2 hours"));

            // at 0, sales may run right up to the event's end, but not past it
            settings(admin, 0, 60).andExpect(status().isOk());
            createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventEnd)).andExpect(status().isCreated());
            createType(window(eventStart.minus(10, ChronoUnit.HOURS), eventEnd.plus(1, ChronoUnit.MINUTES)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("before the event ends (by " + eventEnd + ")")));
        } finally {
            TicketSaleSettings settings = settingsRepository.findById(TicketSaleSettings.ROW_ID).orElseThrow();
            settings.setSalesCloseMinutesBeforeEvent(originalClose);
            settings.setMinSaleDurationMinutes(originalMin);
            settingsRepository.save(settings);
            jdbc.update("delete from audit_log_entries where action = 'ticket_sale_settings.updated' and actor_id = ?", admin.getId());
        }
    }

    // ---- helpers ----

    private String window(Instant start, Instant end) {
        return ",\"saleStartAt\":\"" + start + "\",\"saleEndAt\":\"" + end + "\"";
    }

    /** A GA type named "General"; {@code extra} adds or overrides fields (sale window defaults: now until 5 days on). */
    private ResultActions createType(String extra) throws Exception {
        String window = extra.contains("saleEndAt") ? "" : ",\"saleEndAt\":\"" + Instant.now().plus(5, ChronoUnit.DAYS) + "\"";
        String body = "{\"name\":\"General\",\"kind\":\"GENERAL_ADMISSION\",\"price\":{\"amount\":1000,\"currency\":\"USD\"},"
                + "\"quantityTotal\":100" + window + extra + "}";
        ResultActions result = as(post("/api/v1/events/{eventId}/ticket-types", eventId).contentType("application/json").content(body), owner);
        if (result.andReturn().getResponse().getStatus() == 201) {
            ticketTypeIds.add(UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText()));
        }
        return result;
    }

    private ResultActions edit(UUID id, String body) throws Exception {
        return as(patch("/api/v1/ticket-types/{id}", id).contentType("application/json").content(body), owner);
    }

    private ResultActions settings(User caller, int close, int min) throws Exception {
        return as(put("/api/v1/platform-settings/ticket-sales").contentType("application/json")
                .content("{\"salesCloseMinutesBeforeEvent\":" + close + ",\"minSaleDurationMinutes\":" + min + "}"), caller);
    }

    private ResultActions addToCart(UUID cartId, UUID typeId, int quantity) throws Exception {
        return as(post("/api/v1/carts/{cartId}/items", cartId).contentType("application/json")
                .content("{\"ticketTypeId\":\"" + typeId + "\",\"quantity\":" + quantity + "}"), buyer);
    }

    /** An earlier paid order of the buyer's with {@code count} tickets of the type. */
    private void paidOrder(UUID typeId, int count) {
        UUID orderId = orderRepository.save(Order.builder().buyerId(buyer.getId()).payeeType(PayeeType.ORGANIZATION).payeeId(orgId)
                .status(OrderStatus.PAID).total(Money.builder().amount(1000L * count).currency("USD").build())
                .createdBy(buyer.getId().toString()).build()).getId();
        orderIds.add(orderId);
        for (int i = 0; i < count; i++) {
            ticketRepository.save(Ticket.builder().id(UUID.randomUUID()).orderId(orderId).eventId(eventId).ticketTypeId(typeId)
                    .ownerId(buyer.getId()).ticketNumber("LIM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                    .credential("test-credential-" + UUID.randomUUID()).credentialVersion(0).status(TicketStatus.VALID).build());
        }
    }

    private ResultActions as(MockHttpServletRequestBuilder request, User caller) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(caller)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private static User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("limits-" + UUID.randomUUID() + "@test.local").role(role).build();
    }
}
